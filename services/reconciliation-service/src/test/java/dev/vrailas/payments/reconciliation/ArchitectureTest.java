package dev.vrailas.payments.reconciliation;

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
        classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("dev.vrailas.payments.reconciliation");
        org.assertj.core.api.Assertions.assertThat(classes.size()).as("classes imported").isGreaterThan(10);
    }

    @Test
    void domainDoesNotDependOnApplicationAdaptersOrFrameworks() {
        noClasses().that().resideInAPackage("..reconciliation.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..reconciliation.application..", "..reconciliation.adapter..", "..reconciliation.config..",
                        "org.springframework..", "jakarta.persistence..", "com.rabbitmq..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnInboundAdapters() {
        noClasses().that().resideInAPackage("..reconciliation.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..reconciliation.adapter.in..")
                .check(classes);
    }

    @Test
    void persistenceDoesNotDependOnApplicationOrWeb() {
        noClasses().that().resideInAPackage("..reconciliation.adapter.out..")
                .should().dependOnClassesThat().resideInAnyPackage("..reconciliation.application..", "..reconciliation.adapter.in..")
                .check(classes);
    }
}
