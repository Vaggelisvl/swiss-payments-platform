package dev.vrailas.payments.assistant;

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
        classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("dev.vrailas.payments.assistant");
        org.assertj.core.api.Assertions.assertThat(classes.size()).as("classes imported").isGreaterThan(5);
    }

    @Test
    void domainDoesNotDependOnApplicationAdaptersOrFrameworks() {
        noClasses().that().resideInAPackage("..assistant.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..assistant.application..", "..assistant.adapter..", "..assistant.config..",
                        "org.springframework..", "jakarta.persistence..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnInboundAdapters() {
        noClasses().that().resideInAPackage("..assistant.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..assistant.adapter.in..")
                .check(classes);
    }

    @Test
    void persistenceDoesNotDependOnApplicationOrWeb() {
        noClasses().that().resideInAPackage("..assistant.adapter.out..")
                .should().dependOnClassesThat().resideInAnyPackage("..assistant.application..", "..assistant.adapter.in..")
                .check(classes);
    }
}
