package com.example.refurbished.customer;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.refurbished.audit.AuditService;
import com.example.refurbished.common.api.PageResponse;
import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.common.exception.ResourceNotFoundException;
import com.example.refurbished.customer.dto.CreateCustomerRequest;
import com.example.refurbished.customer.dto.CustomerResponse;
import com.example.refurbished.customer.dto.UpdateCustomerRequest;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;
    private final AuditService audit;

    public CustomerService(CustomerRepository customers, AuditService audit) {
        this.customers = customers;
        this.audit = audit;
    }
    
    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        if (customers.existsByPhoneNumber(request.phoneNumber())) {
            throw new BusinessConflictException("A customer with this phone number already exists.");
        }
        Customer customer = new Customer(request.phoneNumber(), request.fullName(), request.email());
        customers.saveAndFlush(customer);
        audit.record("CUSTOMER_CREATED", "CUSTOMER", customer.getId(), Map.of("phone", customer.getPhoneNumber()));
        return CustomerResponse.from(customer);
    }

    // Lấy hoặc tạo khách hàng theo số điện thoại 
    // Dùng Database lock (FOR UPDATE) tránh duplicate khi 2 request cùng phone
    //     Logic:
    // ├─ Nếu phoneNumber null/trống → return null
    // ├─ Tìm khách hàng theo số điện thoại (với khóa FOR UPDATE)
    // │  ├─ Nếu tồn tại → return khách hàng đó
    // │  └─ Nếu không tồn tại:
    // │     ├─ Tạo khách hàng mới
    // │     ├─ Lưu database
    // │     └─ Ghi nhật ký "CUSTOMER_CREATED"
    @Transactional
    public Customer getOrCreate(String phoneNumber, String fullName, String email) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }

        // Tìm +lock --> Nếu có trả về, không có thì tạo mới + audit 
        return customers.findByPhoneNumberForUpdate(phoneNumber.trim())
                .orElseGet(() -> {
                    Customer customer = new Customer(phoneNumber, fullName, email);
                    customers.saveAndFlush(customer);
                    audit.record("CUSTOMER_CREATED", "CUSTOMER", customer.getId(), Map.of("phone", customer.getPhoneNumber()));
                    return customer;
                });
    }

    public CustomerResponse get(UUID id) {
        return CustomerResponse.from(customers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found.")));
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        // Khóa Customer trong lúc cập nhật để tránh ghi đè khi nhiều request cùng sửa
        Customer customer = customers.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        customer.update(request.fullName(), request.email());
        // Đồnh bộ thay đổi xuống DB trước khi ghi nhận sự kiện cập nhật

        customers.flush();

        //Ghi nhận Customer đã thay đổi; nghiệp vụ hiện tại không yêu cầu metadata chi tiết 
        audit.record("CUSTOMER_UPDATED", "CUSTOMER", customer.getId(), Map.of());
        return CustomerResponse.from(customer);
    }

    public PageResponse<CustomerResponse> list(String query, Pageable pageable) {
        if (query != null && !query.isBlank()) {
            return PageResponse.from(customers.search(query.trim(), pageable).map(CustomerResponse::from));
        }
        return PageResponse.from(customers.findAll(pageable).map(CustomerResponse::from));
    }
}
