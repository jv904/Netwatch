package com.netwatch.netwatch_api.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

final class DeviceSpecifications {

    private DeviceSpecifications() {
    }

    static Specification<Device> matching(DeviceFilter filter) {
        List<Specification<Device>> specs = new ArrayList<>();

        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.type() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("type"), filter.type()));
        }
        if (filter.recognized() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("recognized"), filter.recognized()));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String pattern = "%" + escapeLike(filter.search().trim().toLowerCase(Locale.ROOT)) + "%";
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("ipAddress")), pattern, '\\'),
                    cb.like(cb.lower(root.get("macAddress")), pattern, '\\'),
                    cb.like(cb.lower(root.get("hostname")), pattern, '\\'),
                    cb.like(cb.lower(root.get("vendor")), pattern, '\\'),
                    cb.like(cb.lower(root.get("customName")), pattern, '\\')));
        }
        return Specification.allOf(specs);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
