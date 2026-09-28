package com.example.refurbished.inventory;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DeviceUnitRepository extends JpaRepository<DeviceUnit, UUID>, JpaSpecificationExecutor<DeviceUnit> {
    // Khi cần sửa trạng thái/tình trạng thiết bị, khóa row để tránh 2 request cập nhật cùng lúc.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeviceUnit d where d.id = :id")
    Optional<DeviceUnit> findByIdForUpdate(@Param("id") UUID id);

    // Lấy danh sách thiết bị theo sản phẩm, phục vụ màn hình kho / dashboard.
    Page<DeviceUnit> findByProductId(UUID productId, Pageable pageable);

    // Lọc theo trạng thái thiết bị: available, sold, reserved, under inspection...
    Page<DeviceUnit> findByStatus(DeviceStatus status, Pageable pageable);

    // Kết hợp cả sản phẩm và trạng thái để tìm đúng tập dữ liệu nghiệp vụ.
    Page<DeviceUnit> findByProductIdAndStatus(UUID productId, DeviceStatus status, Pageable pageable);
}
