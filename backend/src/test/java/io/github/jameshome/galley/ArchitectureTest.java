package io.github.jameshome.galley;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 模块边界与分层约束。
 *
 * <p>骨架期模块包内只有 package-info，没有可检查的类，因此每条规则都允许"空集通过"（{@code
 * allowEmptyShould(true)}）。这不是放宽约束：一旦出现越界实现，规则立即失败；S3 建立实现后，可把该开关逐个收回，恢复 ArchUnit 默认的"空集即失败"。
 *
 * <p>规则的文字依据见工程规范 §3.4「分层与模块边界」。
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("io.github.jameshome.galley");

    @Test
    void domainMustNotDependOnFrameworks() {
        noClasses()
                .that()
                .resideInAPackage("..modules..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..", "com.baomidou..", "jakarta.persistence..")
                .because("domain 存放领域对象与规则，不得依赖框架")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void commonMustNotDependOnModules() {
        noClasses()
                .that()
                .resideInAPackage("..common..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..modules..")
                .because("common 被所有模块依赖，反向依赖会造成环")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void apiMustNotReachInfrastructure() {
        noClasses()
                .that()
                .resideInAPackage("..modules..api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..modules..infrastructure..")
                .because("接口层只能经 application 编排，不得直连持久化实现")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void controllersMustNotExposeDataObjects() {
        noClasses()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("DO")
                .because("Controller 不得直接返回 DO，必须经 DTO 转换")
                .allowEmptyShould(true)
                .check(CLASSES);
    }
}
