package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxLogEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.IoxIliReader;
import ch.interlis.iox_j.PipelinePool;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.iox_j.utility.ReaderFactory;
import ch.interlis.iox_j.validator.InterlisFunction;
import ch.interlis.iox_j.validator.ValidationConfig;
import ch.interlis.iox_j.validator.Validator;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public final class ValidationTestHelper {

    private final HashMap<String, Class<InterlisFunction>> userFunctions = new HashMap<>();
    private LogCollector logCollector;

    public void runValidation(String[] dataFiles, String[] modelFiles) throws IoxException, Ili2cFailure {
        dataFiles = addLeadingTestDataDirectory(dataFiles);
        modelFiles = addLeadingTestDataDirectory(modelFiles);
        modelFiles = appendFunctionsExtIli(modelFiles);

        logCollector = new LogCollector();
        LogEventFactory errFactory = new LogEventFactory();
        errFactory.setLogger(logCollector);

        Settings settings = new Settings();
        settings.setTransientObject(ch.interlis.iox_j.validator.Validator.CONFIG_CUSTOM_FUNCTIONS, userFunctions);

        TransferDescription td = ch.interlis.ili2c.Ili2c.compileIliFiles(new ArrayList<>(Arrays.asList(modelFiles)), new ArrayList<String>());

        ValidationConfig modelConfig = new ValidationConfig();
        modelConfig.mergeIliMetaAttrs(td);

        PipelinePool pool = new PipelinePool();
        Validator validator = new ch.interlis.iox_j.validator.Validator(td, modelConfig, logCollector, errFactory, pool, settings);

        for (String filename : dataFiles) {
            IoxReader ioxReader = new ReaderFactory().createReader(new java.io.File(filename), errFactory, settings);
            if (ioxReader instanceof IoxIliReader) {
                ((IoxIliReader) ioxReader).setModel(td);

                errFactory.setDataSource(filename);
                td.setActualRuntimeParameter(ch.interlis.ili2c.metamodel.RuntimeParameters.MINIMAL_RUNTIME_SYSTEM01_CURRENT_TRANSFERFILE, filename);
                try {
                    IoxEvent event;
                    do {
                        event = ioxReader.read();
                        validator.validate(event);
                    } while (!(event instanceof EndTransferEvent));
                } finally {
                    ioxReader.close();
                }
            }
        }
    }

    private String[] appendFunctionsExtIli(String[] modelDirs) {
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        String functionsExtIliPath = "src/model/NGK_SO_FunctionsExt.ili";
        ArrayList<String> result = new ArrayList<>();
        result.add(functionsExtIliPath);
        result.addAll(Arrays.asList(modelDirs));
        return result.toArray(new String[0]);
    }

    @SuppressWarnings("unchecked")
    public void addFunction(InterlisFunction function) {
        userFunctions.put(function.getQualifiedIliName(), (Class<InterlisFunction>) function.getClass());
    }

    public String[] addLeadingTestDataDirectory(String[] files) {
        return Arrays
                .stream(files).map(file -> Paths.get("src/test/data", file).toString())
                .distinct()
                .toArray(String[]::new);
    }

    public ArrayList<IoxLogEvent> getErrs() {
        return logCollector.getErrs();
    }

    public ArrayList<IoxLogEvent> getWarn() {
        return logCollector.getWarn();
    }
}
