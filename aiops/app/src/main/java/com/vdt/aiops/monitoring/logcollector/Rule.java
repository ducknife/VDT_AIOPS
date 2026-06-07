package com.vdt.aiops.monitoring.logcollector;

import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Regex to format log */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Rule {
    private Pattern pattern; /* regex */
    private String replacement; /* regex mapping */
}
