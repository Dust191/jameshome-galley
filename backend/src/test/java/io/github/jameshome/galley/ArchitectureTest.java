package io.github.jameshome.galley;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 模块边界与分层约束。
 *
 * <p>S3 已建立实现，四个规则的"空集通过"开关全部收回：规则匹配不到任何类同样视为失败， 避免包名调整后规则悄悄失效。
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
                .check(CLASSES);
    }
}
