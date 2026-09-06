package dev.springdrop.kernel.validation;

import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.reflect.PropertyReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Runs the constraints attached to an object and reports every failure. Each
 * violation carries the path of the value that failed, so a form can bind the
 * message to the field the editor sees.
 */
@Component
public class Validator {

    private final PluginRegistry pluginRegistry;
    private final PropertyReader propertyReader;

    public Validator(PluginRegistry pluginRegistry, PropertyReader propertyReader) {
        this.pluginRegistry = pluginRegistry;
        this.propertyReader = propertyReader;
    }

    public List<ConstraintViolation> validate(Object subject, List<ConstraintSpec> specs) {
        return validate(subject, specs, ValueLookup.permissive());
    }

    public List<ConstraintViolation> validate(Object subject, List<ConstraintSpec> specs, ValueLookup lookup) {
        PluginManager<Constraint> constraints = pluginRegistry.managerFor(Constraint.class);
        ValidationContext context = new ValidationContext(subject, lookup);

        List<ConstraintViolation> violations = new ArrayList<>();
        for (ConstraintSpec spec : specs) {
            Constraint constraint = constraints.get(spec.constraintId());
            Object value = valueAt(subject, spec.propertyPath());
            for (Object checked : valuesToCheck(spec, value)) {
                constraint.validate(checked, spec.options(), context).ifPresent(message ->
                        violations.add(new ConstraintViolation(spec.propertyPath(), message)));
            }
        }
        return List.copyOf(violations);
    }

    /** A per-item constraint checks each value of a multi-valued field on its own. */
    private static List<Object> valuesToCheck(ConstraintSpec spec, Object value) {
        if (spec.perItem() && value instanceof List<?> items) {
            return List.copyOf(items);
        }
        return Collections.singletonList(value);
    }

    private Object valueAt(Object subject, String propertyPath) {
        if (propertyPath.isEmpty()) {
            return subject;
        }
        return propertyReader.read(subject, List.of(propertyPath.split("\\.")));
    }
}
