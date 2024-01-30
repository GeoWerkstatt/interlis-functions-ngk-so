package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.iox.IoxException;
import com.vividsolutions.jts.util.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class IsInsideAreaByCodeEnumIoxPluginTest {
    private static final String ILI_FILE = "IsInsideAreaByCodeEnum/SetConstraints.ili";
    private static final String TEST_DATA_OK = "IsInsideAreaByCodeEnum/TestData_Ok.xtf";
    private static final String TEST_DATA_FAIL = "IsInsideAreaByCodeEnum/TestData_Fail.xtf";
    private ValidationTestHelper vh = null;

    @BeforeEach
    public void setUp() {
        vh = new ValidationTestHelper();
        vh.addFunction(new IsInsideAreaByCodeEnumIoxPlugin());
    }

    @Test
    public void setConstraintOk() throws Ili2cFailure, IoxException {
        vh.runValidation(new String[]{TEST_DATA_OK}, new String[]{ILI_FILE});
        Assert.equals(0, vh.getErrs().size());
        AssertionHelper.assertNoConstraintError(vh, "insideAreaConstraint");
    }

    @Test
    public void setConstraintFail() throws Ili2cFailure, IoxException {
        vh.runValidation(new String[]{TEST_DATA_FAIL}, new String[]{ILI_FILE});
        Assert.equals(1, vh.getErrs().size());
        AssertionHelper.assertConstraintErrors(vh, 1, "insideAreaConstraint");
    }
}
