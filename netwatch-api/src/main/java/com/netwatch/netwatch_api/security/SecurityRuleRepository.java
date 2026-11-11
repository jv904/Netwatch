package com.netwatch.netwatch_api.security;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityRuleRepository extends JpaRepository<SecurityRule, RuleType> {
}
