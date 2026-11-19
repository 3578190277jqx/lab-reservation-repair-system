package com.lab.audit;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuditActor {
    private final Long id;
    private final String name;
}
