package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.iox.IoxException;
import com.vividsolutions.jts.util.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class IsInsideAreaByCodeIoxPluginTest {
    private static final String ILI_FILE = "IsInsideAreaByCode/SetConstraints.ili";
    private static final String TEST_DATA_OK = "IsInsideAreaByCode/TestData_Ok.xtf";
    private static final String TEST_DATA_FAIL = "IsInsideAreaByCode/TestData_Fail.xtf";
    private ValidationTestHelper vh = null;

    @BeforeEach
    public void setUp() {
        vh = new ValidationTestHelper(new IsInsideAreaByCodeIoxPlugin());
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
}
