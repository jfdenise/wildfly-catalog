/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jboss.wildscribe.site.Generator;
import org.jboss.wildscribe.site.Version;

/**
 *
 * @author jdenise
 */
public class FeaturePackDocGenerator {

    public static void generate(Path directory, Path metadata, Path model) throws Exception {
        Path wildscribeTargetDirectory = directory.resolve("wildscribe");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(metadata.toFile());
        ObjectNode fpNode = mapper.createObjectNode();
        String groupId = node.get("groupId").asText();
        String artifactId = node.get("artifactId").asText();
        String version = node.get("version").asText();
        fpNode.put("mavenCoordinates", groupId+":"+artifactId+":"+version);
        String name = node.get("name").asText();
        fpNode.put("name", name);
        fpNode.put("description", node.get("description").asText());
        fpNode.putIfAbsent("license", node.get("license"));
        fpNode.put("projectURL", node.get("url").asText());
        fpNode.put("scm-url", node.get("scm-url").asText());
        ArrayNode layersArray = (ArrayNode) node.get("layers");
        Iterator<JsonNode> layers = layersArray.elements();
        ArrayNode layersArrayTarget = mapper.createArrayNode();
        fpNode.set("layers", layersArrayTarget);
        Set<String> layersSet = new TreeSet<>();
        while (layers.hasNext()) {
            JsonNode layer = layers.next();
            layersSet.add(layer.get("name").asText());
        }
        for (String n : layersSet) {
            layersArrayTarget.add(n);
        }
        Map<String, Map<String, JsonNode>> categories = new TreeMap<>();
        if (model != null) {
            //JsonNode model = mapper.readTree(modelFile.toFile().toURI().toURL());
            List<Version> versions = Collections.singletonList(new Version(name, version, model.toFile()));
            String directoryName = (groupId + '_' + artifactId);
            fpNode.put("modelReference", "wildscribe/" + directoryName + "/index.html");
            Generator.generate(versions, wildscribeTargetDirectory.resolve(directoryName));
        }
        Properties glowRulesDescriptions = new Properties();
        URL rulesURL = FeaturePackDocGenerator.class.getResource("rules.properties");
        try (InputStream in = rulesURL.openStream()) {
            glowRulesDescriptions.load(in);
        }
        URL commonsURL = FeaturePackDocGenerator.class.getResource("catalog-commons.json");
        JsonNode commons = mapper.readTree(commonsURL);
        Main.generateCatalog(node, glowRulesDescriptions, categories, mapper, wildscribeTargetDirectory);
        
        ObjectNode target = mapper.createObjectNode();
        target.set("legend", commons.get("legend"));
        ArrayNode categoriesArray = mapper.createArrayNode();
        target.putIfAbsent("categories", categoriesArray);
        for (Map.Entry<String, Map<String, JsonNode>> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            ObjectNode category = mapper.createObjectNode();
            category.put("name", categoryName);
            ArrayNode categoryLayers = mapper.createArrayNode();
            category.put("functionalities", categoryLayers);
            for (Map.Entry<String, JsonNode> layersInCategory : entry.getValue().entrySet()) {
                categoryLayers.add(layersInCategory.getValue());
            }
            categoriesArray.add(category);
        }
        Path json = directory.resolve("wildfly-catalog.json");
        Files.deleteIfExists(json);
        mapper.writerWithDefaultPrettyPrinter().writeValue(json.toFile(), target);
    }
}
