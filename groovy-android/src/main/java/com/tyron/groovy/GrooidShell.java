package com.tyron.groovy;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;

import org.codehaus.groovy.control.CompilerConfiguration;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import dalvik.system.InMemoryDexClassLoader;
import groovy.lang.GrooidClassLoader;
import groovy.lang.Script;

// TODO: Handle API 26, rethrow exceptions instead of swallowing them
public class GrooidShell {

    private final ClassLoader classLoader;

    public GrooidShell(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @RequiresApi(api = Build.VERSION_CODES.O_MR1)
    public Object evaluate(String scriptText) {
        D8Command.Builder builder = D8Command.builder();
        builder.setDisableDesugaring(true);
        builder.setMinApiLevel(26);

        final Set<String> classNames = new LinkedHashSet<>();
        CompilerConfiguration config = new CompilerConfiguration();
        config.setBytecodePostprocessor((name, original) -> {
            builder.addClassProgramData(original, Origin.unknown());
            classNames.add(name);
            return original;
        });

        GrooidClassLoader gcl = new GrooidClassLoader(classLoader, config);
        gcl.parseClass(scriptText);

        final ByteBuffer[] byteBuffer = new ByteBuffer[1];

        builder.setProgramConsumer(new DexIndexedConsumer() {
            @Override
            public void finished(DiagnosticsHandler diagnosticsHandler) {

            }

            @Override
            public void accept(int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
                byteBuffer[0] = ByteBuffer.wrap(data.getBuffer());
            }
        });

        try {
            D8.run(builder.build());
        } catch (CompilationFailedException e) {
            e.printStackTrace();
        }

        Map<String, Class<?>> classes = defineDynamic(classNames, byteBuffer);
        for (Class<?> scriptClass : classes.values()) {
            if (Script.class.isAssignableFrom(scriptClass)) {
                Script script;
                try {
                    script = (Script) scriptClass.newInstance();
                    return script.run();
                } catch (IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O_MR1)
    private Map<String, Class<?>> defineDynamic(Set<String> classNames, ByteBuffer[] byteBuffer) {
        InMemoryDexClassLoader classLoader = new InMemoryDexClassLoader(byteBuffer, this.classLoader);
        Map<String, Class<?>> result = new LinkedHashMap<>();
        try {
            for (String className : classNames) {
                result.put(className, classLoader.loadClass(className));
            }
        } catch (ReflectiveOperationException e) {
            Log.e("DynamicLoading", "Unable to load class", e);
        }
        return result;
    }
}
