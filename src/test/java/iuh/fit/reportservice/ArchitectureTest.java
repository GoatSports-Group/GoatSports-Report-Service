package iuh.fit.reportservice;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Khóa quy tắc phụ thuộc của clean architecture: phụ thuộc chỉ được hướng vào trong
 * (presentation/infrastructure → application → domain). Ai thêm import sai tầng thì build fail ngay,
 * thay vì kiến trúc mục dần theo thời gian.
 *
 * <p>Bản sao giống hệt ở mọi service (chỉ khác package gốc): các service là project Maven độc lập,
 * không có module test dùng chung.
 */
@AnalyzeClasses(packages = "iuh.fit.reportservice", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Domain là lõi nghiệp vụ thuần: không biết đến use case, framework hay cách lưu trữ. */
    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage("iuh.fit.reportservice.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "iuh.fit.reportservice.application..", "iuh.fit.reportservice.infrastructure..", "iuh.fit.reportservice.presentation..",
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..", "com.fasterxml..")
            .allowEmptyShould(true);

    /** Application chỉ nói chuyện với bên ngoài qua port (interface); adapter nằm ở infrastructure. */
    @ArchTest
    static final ArchRule applicationDependsOnPortsOnly = noClasses().that().resideInAPackage("iuh.fit.reportservice.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "iuh.fit.reportservice.infrastructure..", "iuh.fit.reportservice.presentation..",
                    "jakarta.persistence..", "org.springframework.web..", "org.springframework.data.jpa..",
                    "org.springframework.kafka..", "feign..")
            .allowEmptyShould(true);

    /** Controller gọi use case, không chạm thẳng vào repository JPA hay client của service khác. */
    @ArchTest
    static final ArchRule presentationGoesThroughApplication = noClasses().that().resideInAPackage("iuh.fit.reportservice.presentation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "iuh.fit.reportservice.infrastructure.persistence..", "iuh.fit.reportservice.infrastructure.repository..",
                    "iuh.fit.reportservice.infrastructure.external..")
            .allowEmptyShould(true);

    /** Adapter không được phụ thuộc ngược lên tầng giao tiếp HTTP. */
    @ArchTest
    static final ArchRule infrastructureDoesNotKnowPresentation = noClasses().that().resideInAPackage("iuh.fit.reportservice.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("iuh.fit.reportservice.presentation..")
            .allowEmptyShould(true);
}
