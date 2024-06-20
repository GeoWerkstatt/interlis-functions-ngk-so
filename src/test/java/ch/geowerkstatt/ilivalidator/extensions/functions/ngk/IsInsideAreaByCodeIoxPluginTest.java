package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxException;
import com.vividsolutions.jts.util.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IsInsideAreaByCodeIoxPluginTest {
    private static final String ILI_FILE = "IsInsideAreaByCode/SetConstraints.ili";
    private static final String TEST_DATA_OK = "IsInsideAreaByCode/TestData_Ok.xtf";
    private static final String TEST_DATA_FAIL = "IsInsideAreaByCode/TestData_Fail.xtf";
    private static final String MODEL = "TestSuite";
    private static final String TOPIC = MODEL + ".FunctionTestTopic";
    private static final String TEST_CLASS = TOPIC + ".TestClass";
    private ValidationTestHelper vh = null;

    @BeforeEach
    public void setUp() {
        vh = new ValidationTestHelper(new IsInsideAreaByCodeIoxPlugin());
    }

    @AfterEach
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        // Clear the cache
        Field field = IsInsideAreaByCodeIoxPlugin.class.getDeclaredField("OBJECTS_CACHE");
        field.setAccessible(true);
        Map<?, ?> cache = (Map<?, ?>) field.get(null);
        cache.clear();
    }

    @Test
    public void setConstraintOk() throws Ili2cFailure, IoxException {
        LogCollector logger = vh.runValidation(new String[]{TEST_DATA_OK}, new String[]{ILI_FILE});
        Assert.equals(0, logger.getErrs().size());
        AssertionHelper.assertNoConstraintError(logger, "insideAreaConstraintEnum");
        AssertionHelper.assertNoConstraintError(logger, "insideAreaConstraintNumeric");
    }

    @Test
    public void setConstraintFail() throws Ili2cFailure, IoxException {
        LogCollector logger = vh.runValidation(new String[]{TEST_DATA_FAIL}, new String[]{ILI_FILE});
        Assert.equals(9, logger.getErrs().size());

        AssertionHelper.assertLogEventsMessages(logger.getErrs(), "^IsInsideAreaByCode found an invalid overlap between code 'code_2' and 'code_3'", 1);
        AssertionHelper.assertLogEventsMessages(logger.getErrs(), "^IsInsideAreaByCode found an invalid overlap between code 'code_3' and 'code_4'", 1);
        AssertionHelper.assertLogEventsMessages(logger.getErrs(), "^IsInsideAreaByCode found an invalid overlap between code '22' and '33'", 1);
        AssertionHelper.assertLogEventsMessages(logger.getErrs(), "^IsInsideAreaByCode found an invalid overlap between code '33' and '44'", 1);
        AssertionHelper.assertLogEventsMessages(logger.getErrs(), "^Custom message\\.$", 4);
        AssertionHelper.assertConstraintErrors(logger, 1, "insideAreaConstraintNumeric");
    }

    @Test
    public void isInsideAreaByCode() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Arrays.asList(() -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o1");
            object.setattrvalue("code", "code_10");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("20", "20", "40", "40"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o2");
            object.setattrvalue("code", "code_magenta_20");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("10", "10", "50", "50"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o3");
            object.setattrvalue("code", "code_40");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("0", "0", "60", "60"));
            return object;
        });

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        Assert.equals(0, logger.getErrs().size());
    }

    @Test
    public void multipleCodesWithSameOrderingOverlap() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Arrays.asList(() -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o1");
            object.setattrvalue("code", "code_blue_20");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("20", "20", "30", "70"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o2");
            object.setattrvalue("code", "code_magenta_20");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("30", "20", "40", "70"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o3");
            object.setattrvalue("code", "code_30");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("0", "0", "60", "30"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o4");
            object.setattrvalue("code", "code_30");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("0", "30", "60", "60"));
            return object;
        });

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        AssertionHelper.assertEventMessagesAreEqual(logger.getErrs(),
                "IsInsideAreaByCode found an invalid overlap between code 'code_blue_20, code_magenta_20' and 'code_30'. The offending geometry has it's centroid at point: POINT (30 65)",
                "Set Constraint TestSuite.FunctionTestTopic.TestClass.insideAreaConstraint is not true.");
    }

    @Test
    public void multiplePolygonsPerCode() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Arrays.asList(() -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o1");
            object.setattrvalue("code", "code_blue_20");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("20", "20", "30", "40"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o2");
            object.setattrvalue("code", "code_magenta_20");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("30", "20", "40", "40"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o3");
            object.setattrvalue("code", "code_30");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("0", "0", "60", "30"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o4");
            object.setattrvalue("code", "code_30");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("0", "30", "60", "60"));
            return object;
        });

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        Assert.equals(0, logger.getErrs().size());
    }

    @Test
    public void disjointInvalidPolygons() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Arrays.asList(() -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o1");
            object.setattrvalue("code", "code_10");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("10", "10", "25", "50"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o2");
            object.setattrvalue("code", "code_30");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("35", "10", "50", "50"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o3");
            object.setattrvalue("code", "code_40");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("0", "0", "60", "60"));
            return object;
        });

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        AssertionHelper.assertEventMessagesAreEqual(logger.getErrs(),
                "IsInsideAreaByCode found an invalid overlap between code 'code_10' and 'code_30'. The offending geometry has it's centroid at point: POINT (17.5 30)",
                "Set Constraint TestSuite.FunctionTestTopic.TestClass.insideAreaConstraint is not true.");
    }

    @Test
    public void allCodesStacked() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Stream
                .of("code_10",
                        "code_blue_20",
                        "code_magenta_20",
                        "code_30",
                        "code_40",
                        "code_noNumber15",
                        "code_without_number",
                        "code_")
                .map(code -> (Supplier<IomObject>) () -> {
                    IomObject object = new Iom_jObject(TEST_CLASS, "obj-" + code);
                    object.setattrvalue("code", code);
                    object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("10", "10", "50", "50"));
                    return object;
                })
                .collect(Collectors.toList());

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        Assert.equals(0, logger.getErrs().size());
    }

    @Test
    public void allCodesStackedOverlap() throws Ili2cFailure {
        AtomicInteger index = new AtomicInteger(0);
        List<Supplier<IomObject>> objects = Stream
                .of("code_10",
                        "code_blue_20",
                        "code_magenta_20",
                        "code_30",
                        "code_40",
                        "code_noNumber15",
                        "code_without_number",
                        "code_")
                .map(code -> (Supplier<IomObject>) () -> {
                    IomObject object = new Iom_jObject(TEST_CLASS, "obj-" + code);
                    object.setattrvalue("code", code);
                    object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("0", "0", String.valueOf(100 - index.incrementAndGet() * 10), "100"));
                    return object;
                })
                .collect(Collectors.toList());

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        AssertionHelper.assertEventMessagesAreEqual(logger.getErrs(),
                "IsInsideAreaByCode found an invalid overlap between code 'code_10' and 'code_blue_20, code_magenta_20'. The offending geometry has it's centroid at point: POINT (85 50)",
                "IsInsideAreaByCode found an invalid overlap between code 'code_blue_20, code_magenta_20' and 'code_30'. The offending geometry has it's centroid at point: POINT (70 50)",
                "IsInsideAreaByCode found an invalid overlap between code 'code_30' and 'code_40'. The offending geometry has it's centroid at point: POINT (55 50)",
                "IsInsideAreaByCode found an invalid overlap between code 'code_40' and 'code_noNumber15, code_, code_without_number'. The offending geometry has it's centroid at point: POINT (45 50)",
                "Set Constraint TestSuite.FunctionTestTopic.TestClass.insideAreaConstraint is not true.");
    }

    @Test
    public void enumWithoutNumber() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Arrays.asList(() -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o1");
            object.setattrvalue("code", "code_10");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("20", "20", "40", "40"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o2");
            object.setattrvalue("code", "code_without_number");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("10", "10", "50", "50"));
            return object;
        });

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        Assert.equals(0, logger.getErrs().size());
    }

    @Test
    public void sharedSegment() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Arrays.asList(() -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o1");
            object.setattrvalue("code", "code_10");
            object.addattrobj("surface", IomObjectHelper.createPolygonFromBoundaries(
                    IomObjectHelper.createBoundary(
                            IomObjectHelper.createCoord("20", "20"),
                            IomObjectHelper.createCoord("20", "40"),
                            IomObjectHelper.createCoord("50", "50"),
                            IomObjectHelper.createCoord("50", "10"),
                            IomObjectHelper.createCoord("20", "20"))));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o2");
            object.setattrvalue("code", "code_without_number");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("10", "10", "50", "50"));
            return object;
        });

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        Assert.equals(0, logger.getErrs().size());
    }

    @Test
    public void collinearSegment() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Arrays.asList(() -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o1");
            object.setattrvalue("code", "code_10");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("20", "20", "50", "40"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o2");
            object.setattrvalue("code", "code_without_number");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("10", "10", "50", "50"));
            return object;
        });

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        Assert.equals(0, logger.getErrs().size());
    }

    @Test
    public void invalidOverlap() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Arrays.asList(() -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o1");
            object.setattrvalue("code", "code_10");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("20", "20", "60", "40"));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o2");
            object.setattrvalue("code", "code_without_number");
            object.addattrobj("surface", IomObjectHelper.createRectangleGeometry("10", "10", "50", "50"));
            return object;
        });

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        AssertionHelper.assertEventMessagesAreEqual(logger.getErrs(),
                "IsInsideAreaByCode found an invalid overlap between code 'code_10' and 'code_without_number'. The offending geometry has it's centroid at point: POINT (55 30)",
                "Set Constraint TestSuite.FunctionTestTopic.TestClass.insideAreaConstraint is not true.");
    }

    @Test
    public void sharedArcSegment() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Arrays.asList(() -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o1");
            object.setattrvalue("code", "code_30");
            object.addattrobj("surface", IomObjectHelper.createPolygonFromBoundaries(
                    IomObjectHelper.createBoundary(
                            IomObjectHelper.createCoord("10", "10"),
                            IomObjectHelper.createCoord("10", "60"),
                            IomObjectHelper.createArc("45", "45", "60", "10"),
                            IomObjectHelper.createCoord("10", "10"))));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o2");
            object.setattrvalue("code", "code_10");
            object.addattrobj("surface", IomObjectHelper.createPolygonFromBoundaries(
                    IomObjectHelper.createBoundary(
                            IomObjectHelper.createCoord("20", "20"),
                            IomObjectHelper.createCoord("10", "60"),
                            IomObjectHelper.createArc("45", "45", "60", "10"),
                            IomObjectHelper.createCoord("20", "20"))));
            return object;
        });

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        Assert.equals(0, logger.getErrs().size());
    }

    @Test
    public void sharedArcSegmentDifferentMidPoint() throws Ili2cFailure {
        List<Supplier<IomObject>> objects = Arrays.asList(() -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o1");
            object.setattrvalue("code", "code_30");
            object.addattrobj("surface", IomObjectHelper.createPolygonFromBoundaries(
                    IomObjectHelper.createBoundary(
                            IomObjectHelper.createCoord("10", "10"),
                            IomObjectHelper.createCoord("10", "60"),
                            IomObjectHelper.createArc("35", "53", "60", "10"),
                            IomObjectHelper.createCoord("10", "10"))));
            return object;
        }, () -> {
            IomObject object = new Iom_jObject(TEST_CLASS, "o2");
            object.setattrvalue("code", "code_10");
            object.addattrobj("surface", IomObjectHelper.createPolygonFromBoundaries(
                    IomObjectHelper.createBoundary(
                            IomObjectHelper.createCoord("20", "20"),
                            IomObjectHelper.createCoord("60", "10"),
                            IomObjectHelper.createArc("53", "35", "10", "60"),
                            IomObjectHelper.createCoord("20", "20"))));
            return object;
        });

        LogCollector logger = vh.runValidation(new String[]{ILI_FILE}, TOPIC, objects.stream().map(Supplier::get).toArray(IomObject[]::new));
        // Because the arcs are stroked differently, thin overlaps occur
        Assert.equals(2, logger.getErrs().size());

        AssertionHelper.assertLogEventsMessages(logger.getErrs(), "^IsInsideAreaByCode found an invalid overlap between code 'code_10' and 'code_30'. The offending geometry has it's centroid at point: POINT \\(43.2\\d+ 42.1\\d+\\)$", 1);
        AssertionHelper.assertLogEventsMessages(logger.getErrs(), "^Set Constraint TestSuite.FunctionTestTopic.TestClass.insideAreaConstraint is not true.$", 1);
    }
}
