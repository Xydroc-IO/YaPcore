// Stamp first-party jars with GPLv3 metadata (see LICENSE · docs/start/LICENSING.md).
import org.gradle.api.tasks.bundling.Jar

subprojects {
    plugins.withId("java") {
        tasks.withType<Jar>().configureEach {
            manifest {
                attributes["Bundle-License"] = "GPL-3.0-or-later"
                attributes["Implementation-Vendor"] = "YapLabs"
            }
        }
    }
}
