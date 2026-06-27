package dev.springdrop.modules.example;

import dev.springdrop.kernel.plugin.SpringDropPlugin;

/**
 * A plugin contributed by the example module, discovered by the kernel's plugin
 * registry through {@link SpringDropPlugin}.
 */
@SpringDropPlugin(id = "example", type = ExampleService.class)
public class ExamplePlugin implements ExampleService {

    @Override
    public String describe() {
        return "example module plugin";
    }
}
