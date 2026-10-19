package dev.vrailas.payments.ingest;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Executable architecture: dependency rules are checked on every build. */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("dev.vrailas.payments.ingest");
        org.assertj.core.api.Assertions.assertThat(classes.size()).as("classes imported").isGreaterThan(10);
    }

    @Test
    void domainDoesNotDependOnApplicationAdaptersOrFrameworks() {
        noClasses().that().resideInAPackage("..ingest.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..ingest.application..", "..ingest.adapter..", "..ingest.config..",
                        "org.springframework..", "jakarta.persistence..", "com.rabbitmq..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnInboundAdapters() {
        noClasses().that().resideInAPackage("..ingest.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..ingest.adapter.in..")
                .check(classes);
    }

    @Test
    void persistenceDoesNotDependOnApplicationOrWeb() {
        noClasses().that().resideInAPackage("..ingest.adapter.out..")
                .should().dependOnClassesThat().resideInAnyPackage("..ingest.application..", "..ingest.adapter.in..")
                .check(classes);
    }
}
