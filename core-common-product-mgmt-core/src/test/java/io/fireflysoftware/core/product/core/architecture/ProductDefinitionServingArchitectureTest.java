package io.fireflysoftware.core.product.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

class ProductDefinitionServingArchitectureTest {
    @Test
    void productJavaPackagesHaveNoVersionDirectorySegments() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(root.resolve("core-common-product-mgmt-interfaces"))) root = root.getParent();
        var violations = new ArrayList<String>();
        for (String module : new String[] {"core", "interfaces", "models", "web", "sdk"}) {
            var sources = root.resolve("core-common-product-mgmt-" + module).resolve("src");
            if (!Files.isDirectory(sources)) continue;
            try (var files = Files.walk(sources)) {
                for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    for (var segment : file) {
                        if (segment.toString().matches("v[0-9]+")) {
                            violations.add(root.relativize(file).toString());
                            break;
                        }
                    }
                    if (Files.readString(file).contains(".definition." + "v1"))
                        violations.add("Version package reference in " + root.relativize(file));
                }
            }
        }
        assertThat(violations).isEmpty();
    }
}
