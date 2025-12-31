package io.github.nicechester.bibleai.service;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.MiroStat;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;

@Log4j2
public class LlamaService {
    private final LlamaModel model;
    private final float temperature;

    public LlamaService(@Value("${llm.model.path:}") String modelPath,
                       @Value("${llm.model.ngpu:0}") int ngpuLayers,
                       @Value("${llm.model.temperature:0.7}") float temperature,
                       ResourceLoader resourceLoader) {
        
        if (modelPath == null || modelPath.isEmpty()) {
            log.warn("Llama model path not configured. LlamaService will not be available.");
            this.model = null;
            this.temperature = temperature;
            return;
        }

        // Resolve classpath resources to actual file paths
        String resolvedPath = resolveModelPath(modelPath, resourceLoader);
        
        if (resolvedPath == null) {
            log.warn("Could not resolve Llama model path: {}. LlamaService will not be available.", modelPath);
            this.model = null;
            this.temperature = temperature;
            return;
        }

        log.info("Loading Llama model from path: {} (GPU layers: {})", resolvedPath, ngpuLayers);
        ModelParameters modelParams = new ModelParameters()
                .setModelFilePath(resolvedPath)
                .setNGpuLayers(ngpuLayers);
        this.temperature = temperature;
        this.model = new LlamaModel(modelParams);
        log.info("Llama model loaded successfully");
    }
    
    /**
     * Resolves a model path, handling both classpath resources and file system paths.
     * 
     * @param modelPath The model path (can be classpath:... or a file system path)
     * @param resourceLoader Spring ResourceLoader for resolving classpath resources
     * @return Resolved file system path, or null if resolution fails
     */
    private String resolveModelPath(String modelPath, ResourceLoader resourceLoader) {
        try {
            if (modelPath.startsWith("classpath:")) {
                // Handle classpath resources
                Resource resource = resourceLoader.getResource(modelPath);
                if (resource.exists()) {
                    File file = resource.getFile();
                    return file.getAbsolutePath();
                } else {
                    log.error("Classpath resource not found: {}", modelPath);
                    return null;
                }
            } else {
                // Handle file system paths (absolute or relative)
                File file = new File(modelPath);
                if (file.exists()) {
                    return file.getAbsolutePath();
                } else {
                    log.error("Model file not found: {}", modelPath);
                    return null;
                }
            }
        } catch (IOException e) {
            log.error("Error resolving model path: {}", modelPath, e);
            return null;
        }
    }

    public boolean isAvailable() {
        return model != null;
    }

    public String infer(String prompt) {
        if (model == null) {
            throw new IllegalStateException("Llama model is not configured");
        }
        
        InferenceParameters inferParams = new InferenceParameters(prompt)
                .setTemperature(temperature)
                .setPenalizeNl(true)
                .setMiroStat(MiroStat.V2);
        
        StringBuilder response = new StringBuilder();
        for (LlamaOutput output : model.generate(inferParams)) {
            response.append(output);
        }
        return response.toString();
    }
}

