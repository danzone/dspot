package fr.inria.stamp;

import com.martiansoftware.jsap.*;
import fr.inria.diversify.dspot.amplifier.*;
import fr.inria.diversify.dspot.selector.BranchCoverageTestSelector;
import fr.inria.diversify.dspot.selector.PitMutantScoreSelector;
import fr.inria.diversify.dspot.selector.TestSelector;
import fr.inria.diversify.mutant.pit.MavenPitCommandAndOptions;
import fr.inria.diversify.util.Log;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 1/17/17
 */
public class JSAPOptions {

    public static final JSAP options = initJSAP();

    enum SelectorEnum {
        BranchCoverageTestSelector(new BranchCoverageTestSelector(10)),
        PitMutantScoreSelector(new fr.inria.diversify.dspot.selector.PitMutantScoreSelector());
        public final TestSelector testCriterion;

        private SelectorEnum(TestSelector testCriterion) {
            this.testCriterion = testCriterion;
        }
    }

    enum AmplifierEnum {
        MethodAdd(new TestMethodCallAdder()),
        MethodRemove(new TestMethodCallRemover()),
        StatementAdderOnAssert(new StatementAdderOnAssert()),
        TestDataMutator(new TestDataMutator());
        public final Amplifier amplifier;

        private AmplifierEnum(Amplifier amplifier) {
            this.amplifier = amplifier;
        }
    }

    public static Configuration parse(String[] args) {
        JSAPResult jsapConfig = options.parse(args);
        if (!jsapConfig.success() || jsapConfig.getBoolean("help")) {
            System.err.println();
            for (Iterator<?> errs = jsapConfig.getErrorMessageIterator(); errs.hasNext(); ) {
                System.err.println("Error: " + errs.next());
            }
            showUsage();
        } else if (jsapConfig.getBoolean("example")) {
            Main.runExample();
        }

        if (jsapConfig.getString("path") == null) {
            System.err.println("Error: Parameter 'path' is required.");
            showUsage();
        }

        TestSelector testCriterion;
        if (jsapConfig.getString("mutant") != null) {
            if (!"PitMutantScoreSelector".equals(jsapConfig.getString("test-criterion"))) {
                Log.warn("You specify a path to mutations.csv but you did not specified the right test-criterion");
                Log.warn("Forcing the Selector to PitMutantScoreSelector");
            }
            testCriterion = new PitMutantScoreSelector(jsapConfig.getString("mutant"));
        } else {
            testCriterion = SelectorEnum.valueOf(jsapConfig.getString("test-criterion")).testCriterion;
        }

        MavenPitCommandAndOptions.descartesMode = jsapConfig.getBoolean("descartes");
        MavenPitCommandAndOptions.evosuiteMode = jsapConfig.getBoolean("evosuite");

        return new Configuration(jsapConfig.getString("path"),
                buildAmplifiersFromString(jsapConfig.getStringArray("amplifiers")),
                jsapConfig.getInt("iteration"),
                Arrays.asList(jsapConfig.getStringArray("test")),
                jsapConfig.getString("output"),
                testCriterion,
                Arrays.asList(jsapConfig.getStringArray("testClasses")),
                jsapConfig.getLong("seed"),
                jsapConfig.getInt("timeOut"),
                jsapConfig.getString("builder"),
                jsapConfig.getString("mavenHome"));
    }

    private static Amplifier stringToAmplifier(String amplifier) {
        return AmplifierEnum.valueOf(amplifier).amplifier;
    }

    private static List<Amplifier> buildAmplifiersFromString(String[] amplifiersAsString) {
        if (amplifiersAsString == null) {
            return Arrays.stream(new String[]{"MethodAdd", "MethodRemove", "StatementAdderOnAssert", "TestDataMutator"})
                    .map(JSAPOptions::stringToAmplifier)
                    .collect(Collectors.toList());
        } else {
            return Arrays.stream(amplifiersAsString)
                    .map(JSAPOptions::stringToAmplifier)
                    .collect(Collectors.toList());
        }
    }

    private static void showUsage() {
        System.err.println();
        System.err.println("Usage: java -jar target/dspot-1.0.0-jar-with-dependencies.jar");
        System.err.println("                          " + options.getUsage());
        System.err.println();
        System.err.println(options.getHelp());
        System.exit(1);
    }

    private static JSAP initJSAP() {
        JSAP jsap = new JSAP();

        Switch help = new Switch("help");
        help.setLongFlag("help");
        help.setShortFlag('h');
        help.setHelp("shows this help");

        Switch example = new Switch("example");
        example.setLongFlag("example");
        example.setShortFlag('e');
        example.setHelp("run the example of DSpot and leave");

        FlaggedOption pathToConfigFile = new FlaggedOption("path");
        pathToConfigFile.setAllowMultipleDeclarations(false);
        pathToConfigFile.setLongFlag("path-to-properties");
        pathToConfigFile.setShortFlag('p');
        pathToConfigFile.setStringParser(JSAP.STRING_PARSER);
        pathToConfigFile.setUsageName("./path/to/myproject.properties");
        pathToConfigFile.setHelp("[mandatory] specify the path to the configuration file (format Java properties) of the target project (e.g. ./foo.properties).");

        FlaggedOption amplifiers = new FlaggedOption("amplifiers");
        amplifiers.setList(true);
        amplifiers.setLongFlag("amplifiers");
        amplifiers.setShortFlag('a');
        amplifiers.setStringParser(JSAP.STRING_PARSER);
        amplifiers.setUsageName("Amplifier");
        amplifiers.setHelp("[optional] specify the list of amplifiers to use. Default with all available amplifiers. Possible values: MethodAdd|MethodRemove|StatementAdderOnAssert|TestDataMutator");

        FlaggedOption iteration = new FlaggedOption("iteration");
        iteration.setDefault("3");
        iteration.setStringParser(JSAP.INTEGER_PARSER);
        iteration.setShortFlag('i');
        iteration.setLongFlag("iteration");
        iteration.setAllowMultipleDeclarations(false);
        iteration.setHelp("[optional] specify the number of amplification iteration. A larger number may help to improve the test criterion (eg a larger number of iterations mah help to kill more mutants). This has an impact on the execution time: the more iterations, the longer DSpot runs.");

        FlaggedOption selector = new FlaggedOption("test-criterion");
        selector.setAllowMultipleDeclarations(false);
        selector.setLongFlag("test-criterion");
        selector.setShortFlag('s');
        selector.setStringParser(JSAP.STRING_PARSER);
        selector.setUsageName("PitMutantScoreSelector | BranchCoverageTestSelector");
        selector.setHelp("[optional] specify the test adequacy criterion to be maximized with amplification");
        selector.setDefault("PitMutantScoreSelector");

        FlaggedOption specificTestCase = new FlaggedOption("test");
        specificTestCase.setStringParser(JSAP.STRING_PARSER);
        specificTestCase.setShortFlag('t');
        specificTestCase.setList(true);
        specificTestCase.setAllowMultipleDeclarations(false);
        specificTestCase.setLongFlag("test");
        specificTestCase.setDefault("all");
        specificTestCase.setUsageName("my.package.MyClassTest");
        specificTestCase.setHelp("[optional] fully qualified names of test classes to be amplified. If the value is all, DSpot will amplify the whole test suite.");

        FlaggedOption output = new FlaggedOption("output");
        output.setStringParser(JSAP.STRING_PARSER);
        output.setAllowMultipleDeclarations(false);
        output.setShortFlag('o');
        output.setLongFlag("output-path");
        output.setHelp("[optional] specify the output folder (default: dspot-report)");

        FlaggedOption mutantScore = new FlaggedOption("mutant");
        mutantScore.setStringParser(JSAP.STRING_PARSER);
        mutantScore.setAllowMultipleDeclarations(false);
        mutantScore.setShortFlag('m');
        mutantScore.setLongFlag("path-pit-result");
        mutantScore.setUsageName("./path/to/mutations.csv");
        mutantScore.setHelp("[optional, expert mode] specify the path to the .csv of the original result of Pit Test. If you use this option the selector will be forced to PitMutantScoreSelector");

        FlaggedOption testCases = new FlaggedOption("testClasses");
        testCases.setList(true);
        testCases.setAllowMultipleDeclarations(false);
        testCases.setLongFlag("testClasses");
        testCases.setShortFlag('c');
        testCases.setStringParser(JSAP.STRING_PARSER);
        testCases.setHelp("specify the test cases to amplify");

        FlaggedOption seed = new FlaggedOption("seed");
        seed.setStringParser(JSAP.LONG_PARSER);
        seed.setLongFlag("randomSeed");
        seed.setShortFlag('r');
        seed.setUsageName("long integer");
        seed.setHelp("specify a seed for the random object (used for all randomized operation)");
        seed.setDefault("23");

        FlaggedOption timeOut = new FlaggedOption("timeOut");
        timeOut.setStringParser(JSAP.INTEGER_PARSER);
        timeOut.setLongFlag("timeOut");
        timeOut.setShortFlag('v');
        timeOut.setUsageName("long integer");
        timeOut.setHelp("specify the timeout value of the degenerated tests in millisecond");
        timeOut.setDefault("10000");

        FlaggedOption automaticBuilder = new FlaggedOption("builder");
        automaticBuilder.setStringParser(JSAP.STRING_PARSER);
        automaticBuilder.setLongFlag("automatic-builder");
        automaticBuilder.setShortFlag('b');
        automaticBuilder.setUsageName("MavenBuilder | GradleBuilder");
        automaticBuilder.setHelp("[optional] specify the automatic builder to build the project");
        automaticBuilder.setDefault("MavenBuilder");

        FlaggedOption mavenHome = new FlaggedOption("mavenHome");
        mavenHome.setStringParser(JSAP.STRING_PARSER);
        mavenHome.setLongFlag("maven-home");
        mavenHome.setShortFlag('j');
        mavenHome.setUsageName("path to maven home");
        mavenHome.setHelp("specify the path to the maven home");

        Switch descartesMode = new Switch("descartes");
        descartesMode.setShortFlag('d');
        descartesMode.setLongFlag("descartes");

        Switch evosuiteMode = new Switch("evosuite");
        evosuiteMode.setShortFlag('k');
        evosuiteMode.setLongFlag("evosuite");

        try {
            jsap.registerParameter(pathToConfigFile);
            jsap.registerParameter(amplifiers);
            jsap.registerParameter(iteration);
            jsap.registerParameter(selector);
            jsap.registerParameter(descartesMode);
            jsap.registerParameter(evosuiteMode);
            jsap.registerParameter(specificTestCase);
            jsap.registerParameter(testCases);
            jsap.registerParameter(output);
            jsap.registerParameter(mutantScore);
            jsap.registerParameter(automaticBuilder);
            jsap.registerParameter(mavenHome);
            jsap.registerParameter(seed);
            jsap.registerParameter(timeOut);
            jsap.registerParameter(example);
            jsap.registerParameter(help);
        } catch (JSAPException e) {
            throw new RuntimeException(e);
        }

        return jsap;
    }

}