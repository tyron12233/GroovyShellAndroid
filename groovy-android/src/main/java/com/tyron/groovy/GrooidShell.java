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

// TODO: Handle API 26
@RequiresApi(api = Build.VERSION_CODES.O_MR1)
public class GrooidShell {

    private final ClassLoader classLoader;

    public GrooidShell(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Result evaluate(String scriptText) {
        try {
            return evaluateInternal(scriptText);
        } catch (CompilationFailedException e) {
            return Result.failed(e);
        }
    }

    private Result evaluateInternal(String scriptText) throws CompilationFailedException {
        D8Command.Builder builder = D8Command.builder();
        builder.setDisableDesugaring(true);
        builder.setMinApiLevel(26);

        Set<String> classNames = new LinkedHashSet<>();
        CompilerConfiguration config = new CompilerConfiguration();
        config.setBytecodePostprocessor((name, original) -> {
            builder.addClassProgramData(original, Origin.unknown());
            classNames.add(name);
            return original;
        });

        GrooidClassLoader gcl = new GrooidClassLoader(classLoader, config);
        gcl.parseClass(scriptText);

        ByteBuffer[] byteBuffer = getTransformedDexByteBufferArray(builder);
        Map<String, Class<?>> classes = defineDynamic(classNames, byteBuffer);
        for (Class<?> scriptClass : classes.values()) {
            if (Script.class.isAssignableFrom(scriptClass)) {
                try {
                    Script script = (Script) scriptClass.newInstance();
                    return Result.success(script.run());
                } catch (IllegalAccessException | InstantiationException e) {
                    return Result.failed(e);
                }
            }
        }

        return Result.failed(new IllegalArgumentException("Un-parsable argument provided."));
    }

    private ByteBuffer[] getTransformedDexByteBufferArray(D8Command.Builder commandBuilder) throws CompilationFailedException {
        final ByteBuffer[] byteBuffer = new ByteBuffer[1];
        commandBuilder.setProgramConsumer(new DexIndexedConsumer() {
            @Override
            public void finished(DiagnosticsHandler diagnosticsHandler) {

            }

            @Override
            public void accept(int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
                byteBuffer[0] = ByteBuffer.wrap(data.getBuffer());
            }
        });
        D8.run(commandBuilder.build());
        return byteBuffer;
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

    @SuppressWarnings("unused")
    public static class Result {

        public static Result failed(Throwable failure) {
            return new Result(null, failure);
        }

        public static Result success(Object result) {
            return new Result(result, null);
        }

        private final Throwable failure;

        private final Object result;

        private Result(Object result, Throwable failure) {
            this.result = result;
            this.failure = failure;
        }

        public boolean isSuccessful() {
            return failure != null;
        }

        public Throwable getFailure() {
            return failure;
        }

        public Object getResult() {
            return result;
        }
    }
}
