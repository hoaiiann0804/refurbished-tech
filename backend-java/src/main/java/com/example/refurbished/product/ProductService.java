package com.example.refurbished.product;

import com.example.refurbished.audit.AuditService;
import java.util.Map;

import com.example.refurbished.common.api.PageResponse;
import com.example.refurbished.common.exception.ResourceNotFoundException;
import com.example.refurbished.product.dto.CreateProductRequest;
import com.example.refurbished.product.dto.ProductResponse;
import com.example.refurbished.product.dto.UpdateProductRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository repository;
    private final AuditService audit;

    public ProductService(ProductRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = new Product(request.modelCode(), request.name(), request.brand(),
                request.specificationSummary(), request.active() == null || request.active());
        repository.saveAndFlush(product);
        audit.record("PRODUCT_CREATED", "PRODUCT", product.getId(), Map.of("modelCode", product.getModelCode()));
        return ProductResponse.from(product);
    }

    public ProductResponse get(UUID id) {
        return ProductResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found.")));
    }

    public PageResponse<ProductResponse> list(Boolean active, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt", "id").descending());
        Page<Product> result = active == null ? repository.findAll(pageable)
                : repository.findByActive(active, pageable);
        return PageResponse.from(result.map(ProductResponse::from));
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        product.updateDetails(request.name(), request.brand(), request.specificationSummary(), request.active());
        repository.flush();
        audit.record("PRODUCT_UPDATED", "PRODUCT", id, Map.of("active", product.isActive()));
        return ProductResponse.from(product);
    }
}
