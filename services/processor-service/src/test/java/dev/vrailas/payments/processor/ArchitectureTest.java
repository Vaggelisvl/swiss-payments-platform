package dev.vrailas.payments.processor;

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
        classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("dev.vrailas.payments.processor");
        org.assertj.core.api.Assertions.assertThat(classes.size()).as("classes imported").isGreaterThan(10);
    }

    @Test
    void domainDoesNotDependOnApplicationAdaptersOrFrameworks() {
        noClasses().that().resideInAPackage("..processor.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..processor.application..", "..processor.adapter..", "..processor.config..",
                        "org.springframework..", "jakarta.persistence..", "com.rabbitmq..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnInboundAdapters() {
        noClasses().that().resideInAPackage("..processor.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..processor.adapter.in..")
                .check(classes);
    }

    @Test
    void persistenceDoesNotDependOnApplicationOrWeb() {
        noClasses().that().resideInAPackage("..processor.adapter.out..")
                .should().dependOnClassesThat().resideInAnyPackage("..processor.application..", "..processor.adapter.in..")
                .check(classes);
    }
}
