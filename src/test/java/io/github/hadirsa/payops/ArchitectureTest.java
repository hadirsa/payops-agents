package io.github.hadirsa.payops;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Layer rules for every feature package ({@code payops.<feature>.domain|agent|api|infra}).
 * A new feature gets these checks by following the same layout.
 */
@AnalyzeClasses(packages = "io.github.hadirsa.payops", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.stripe..",
                    "com.embabel.agent.api..",
                    "com.embabel.agent.core..",
                    "com.embabel.common..",
                    "..payops.*.agent..",
                    "..payops.*.api..",
                    "..payops.*.infra..")
            .because("domain holds records, pure rules and ports; HasContent from com.embabel.agent.domain is the one allowed Embabel type");

    @ArchTest
    static final ArchRule agentReachesTheOutsideOnlyThroughPorts = noClasses()
            .that().resideInAPackage("..payops.*.agent..")
            .should().dependOnClassesThat().resideInAnyPackage("..payops.*.api..", "..payops.*.infra..", "com.stripe..");

    @ArchTest
    static final ArchRule adaptersGoThroughThePlatform = noClasses()
            .that().resideInAPackage("..payops.*.api..")
            .should().dependOnClassesThat().resideInAnyPackage("..payops.*.agent..", "..payops.*.infra..")
            .because("the api invokes goals by type via AgentInvocation so the planner chooses the path");

    @ArchTest
    static final ArchRule infraDoesNotReachIntoTheApplication = noClasses()
            .that().resideInAPackage("..payops.*.infra..")
            .should().dependOnClassesThat().resideInAnyPackage("..payops.*.agent..", "..payops.*.api..");

    @ArchTest
    static final ArchRule stripeSdkStaysInItsAdapters = noClasses()
            .that().resideOutsideOfPackage("..payops.*.infra.stripe..")
            .should().dependOnClassesThat().resideInAPackage("com.stripe..")
            .because("swapping the payment provider should touch one package, inbound and outbound");

    @ArchTest
    static final ArchRule domainSubPackagesFormNoCycles = slices()
            .matching("io.github.hadirsa.payops.(*).domain.(*)..")
            .should().beFreeOfCycles()
            .because("model <- evidence <- decision <- policy/port keeps the domain readable bottom-up");

    @ArchTest
    static final ArchRule apiSubPackagesFormNoCycles = slices()
            .matching("io.github.hadirsa.payops.(*).api.(*)..")
            .should().beFreeOfCycles()
            .because("rest depends on service, never the other way round");

    @ArchTest
    static final ArchRule featuresAreIndependent = slices()
            .matching("io.github.hadirsa.payops.(*)..")
            .should().notDependOnEachOther();
}
