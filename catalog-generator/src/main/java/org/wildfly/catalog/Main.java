/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {
        String wildflyVersion = System.getProperty("wildfly-version");
        if (wildflyVersion == null) {
            throw new Exception("-Dwildfly-version=<version> must be set");
        }
        boolean validateOnly = Boolean.getBoolean("validateOnly");
        Path rootDirectory = Paths.get("../docs");
        Path targetDirectory = rootDirectory.resolve(wildflyVersion).toAbsolutePath();
        if (!validateOnly) {
            Files.createDirectories(targetDirectory);
        } else {
            System.out.println("Validate only that we can build a catalog");
        }
        Path inputMetadata = Paths.get("../metadata/" + wildflyVersion + "/wildfly-catalog.json").toAbsolutePath();
        if (!Files.exists(inputMetadata)) {
            throw new Exception("File doesn't exist " + inputMetadata + ". Can't enerate catalog");
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(inputMetadata.toFile());
        ObjectNode target = mapper.createObjectNode();
        target.put("description", "Catalog of WildFly " + wildflyVersion + " features (incomplete for now) to provision a WildFly server");
        target.set("documentation", node.get("documentation"));
        target.set("legend", node.get("legend"));

        // Glow rules description
        Properties glowRulesDescriptions = new Properties();
        String rulesURL = node.get("glowRulesDescriptions").asText();
        try (InputStream in = new URL(rulesURL).openStream()) {
            glowRulesDescriptions.load(in);
        }
        Map<String, Map<String, JsonNode>> categories = new TreeMap<>();
        // Old way, based on URL online.
        if (node.has("content")) {
            ArrayNode an = (ArrayNode) node.get("content");
            Iterator<JsonNode> it = an.elements();
            while (it.hasNext()) {
                JsonNode remoteMetadata = it.next();
                String metadataUrl = remoteMetadata.get("url").asText();
                JsonNode subCatalog = mapper.readTree(new URL(metadataUrl));
                generateCatalog(subCatalog, glowRulesDescriptions, categories, mapper);
            }
        } else {
            String url = node.get("knownFeaturePacks").asText();
            JsonNode fpList = mapper.readTree(new URL(url));
            ArrayNode fps = (ArrayNode) fpList.get("featurePacks");
            Iterator<JsonNode> it = fps.elements();
            ArrayNode featurePacks = mapper.createArrayNode();
            target.set("featurePacks", featurePacks);
            while (it.hasNext()) {
                ObjectNode fpNode = mapper.createObjectNode();
                featurePacks.add(fpNode);
                String fp = it.next().asText();
                System.out.println("FP " + fp);
                fpNode.put("mavenCoordinates", fp);
                // Mimic what we would do using maven. here we expect all to be in the cache
                Path home = Paths.get(System.getProperty("user.home")).resolve(".m2").resolve("repository");
                String[] coords = fp.split(":");
                String groupId = coords[0].replaceAll("\\.", File.separator);
                String artifactId = coords[1];
                String version = coords[2];
                String metadataFileName = artifactId + "-" + version + "-metadata.json";
                Path metadataFile = home.resolve(groupId).resolve(artifactId).resolve(version).resolve(metadataFileName);
                JsonNode subCatalog = mapper.readTree(metadataFile.toFile().toURI().toURL());
                fpNode.put("name", subCatalog.get("name").asText());
                fpNode.put("description", subCatalog.get("description").asText());                
                fpNode.putIfAbsent("license", subCatalog.get("license"));
                fpNode.put("projectURL", subCatalog.get("url").asText());
                fpNode.put("scm", subCatalog.get("scm").asText());
                
                ArrayNode layersArray = (ArrayNode) subCatalog.get("layers");
                Iterator<JsonNode> layers = layersArray.elements();
                ArrayNode layersArrayTarget = mapper.createArrayNode();
                fpNode.set("layers", layersArrayTarget);
                Set<String> layersSet = new TreeSet<>();
                while (layers.hasNext()) {
                    JsonNode layer = layers.next();
                    if(!isInternalLayer(layer)) {
                        layersSet.add(layer.get("name").asText());
                    }
                }
                for (String n : layersSet) {
                    layersArrayTarget.add(n);
                }
                String modelFileName = artifactId + "-" + version + "-model.json";
                Path modelFile = home.resolve(groupId).resolve(artifactId).resolve(version).resolve(modelFileName);
                if (Files.exists(modelFile)) {
                    JsonNode model = mapper.readTree(modelFile.toFile().toURI().toURL());
                    fpNode.set("modelReference", model);
                }
                generateCatalog(subCatalog, glowRulesDescriptions, categories, mapper);
            }
        }
        ArrayNode categoriesArray = mapper.createArrayNode();
        target.putIfAbsent("categories", categoriesArray);
        for (Entry<String, Map<String, JsonNode>> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            ObjectNode category = mapper.createObjectNode();
            category.put("name", categoryName);
            ArrayNode categoryLayers = mapper.createArrayNode();
            category.put("functionalities", categoryLayers);
            for (Entry<String, JsonNode> layersInCategory : entry.getValue().entrySet()) {
                categoryLayers.add(layersInCategory.getValue());
            }
            categoriesArray.add(category);
        }
        if (!validateOnly) {
            Path json = targetDirectory.resolve("wildfly-catalog.json");
            Files.deleteIfExists(json);
            mapper.writerWithDefaultPrettyPrinter().writeValue(json.toFile(), target);
        }
        if (!validateOnly) {
            Path viewer = targetDirectory.resolve("index.html");
            Files.deleteIfExists(viewer);
            try (InputStream stream = Main.class.getResourceAsStream("wildfly-catalog-viewer.html")) {
                try (InputStreamReader inputStreamReader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                        List<String> lines = bufferedReader.lines().collect(Collectors.toList());
                        List<String> targetLines = new ArrayList<>();
                        for (String line : lines) {
                            line = line.replace("###REPLACE_WILDFLY_VERSION###", wildflyVersion);
                            targetLines.add(line);
                        }
                        Files.write(viewer, targetLines);
                    }
                }
            }
            String replaceTag = "<!-- ####REPLACE_LAST_VERSION#### -->";
            String newEntry = "<li><a href=\"" + wildflyVersion + "/index.html\">" + wildflyVersion + "</a></li>";
            // Update index
            Path indexFile = rootDirectory.resolve("index.html").toAbsolutePath();
            List<String> lines = Files.readAllLines(indexFile);
            //Check if the index already exists
            boolean exists = false;
            for (String line : lines) {
                if (line.contains(wildflyVersion)) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                System.out.println("Catalog for WildFly " + wildflyVersion + " already exists in the index, index not updated.");
            } else {
                List<String> targetLines = new ArrayList<>();
                for (String line : lines) {
                    if (line.contains(replaceTag)) {
                        targetLines.add(newEntry);
                        targetLines.add(replaceTag);
                    } else {
                        targetLines.add(line);
                    }
                }
                Files.deleteIfExists(indexFile);
                Files.write(indexFile, targetLines);
            }
        } else {
            System.out.println("WildFly catalog generation validated");
        }
    }

    private static void generateCatalog(JsonNode subCatalog, Properties glowRulesDescriptions,
            Map<String, Map<String, JsonNode>> categories, ObjectMapper mapper) throws Exception {
        String fp = subCatalog.get("feature-pack-location").asText();

        ArrayNode layersArray = (ArrayNode) subCatalog.get("layers");
        Iterator<JsonNode> layers = layersArray.elements();
        while (layers.hasNext()) {
            ObjectNode layer = (ObjectNode) layers.next(); 
            layer.put("feature-pack", fp);
            String layerName = layer.get("name").asText();
            ArrayNode props = ((ArrayNode) layer.get("properties"));
            String category = null;
            String description = null;
            String note = null;
            String addOn = null;
            String stability = null;
            List<JsonNode> discoveryRules = new ArrayList<>();
            if (props != null) {
                Iterator<JsonNode> properties = props.elements();
                while (properties.hasNext()) {
                    ObjectNode prop = (ObjectNode) properties.next();
                    String name = prop.get("name").asText();
                    if (name.equals("org.wildfly.category")) {
                        category = prop.get("value").asText();
                        continue;
                    }
                    if (name.equals("org.wildfly.description")) {
                        description = prop.get("value").asText();
                        continue;
                    }
                    if (name.equals("org.wildfly.note")) {
                        note = prop.get("value").asText();
                        continue;
                    }
                    if (name.equals("org.wildfly.stability")) {
                        stability = prop.get("value").asText();
                        continue;
                    }
                    if (name.equals("org.wildfly.rule.add-on")) {
                        String val = prop.get("value").asText();
                        addOn = val.split(",")[1];
                        continue;
                    }
                    if (name.equals("org.wildfly.rule.kind")) {
                        String val = prop.get("value").asText();
                        if (val.equals("default-base-layer")) {
                            discoveryRules.add(prop);
                            setRuleDescription(name, glowRulesDescriptions, prop);
                        }
                        continue;
                    }
                    if (name.startsWith("org.wildfly.rule") && !name.startsWith("org.wildfly.rule.add-on")) {
                        discoveryRules.add(prop);
                        setRuleDescription(name, glowRulesDescriptions, prop);
                        continue;
                    }
                }
                layer.remove("properties");
            }
            if (category == null) {
                category = "Internal";
                // Internal without any content are not taken into account
                if(layer.get("managementModel").isEmpty() && 
                        !layer.has("dependencies") && !layer.has("packages")) {
                    System.out.println("Internal with metadata only, ignoring " + layer.get("name").asText() + " of " + fp);
                    continue;
                }
            }
            if (description != null) {
                layer.put("description", description);
            }
            if (note != null) {
                layer.put("note", note);
            }
            if (addOn != null) {
                layer.put("glowAddOn", addOn);
            }
            if (stability == null) {
                layer.put("stability", "default");
            } else {
                layer.put("stability", stability);
            }
            if (!discoveryRules.isEmpty()) {
                ArrayNode rules = mapper.createArrayNode();
                rules.addAll(discoveryRules);
                layer.putIfAbsent("glowRules", rules);
            }
            layer.put("glowDiscoverable", !discoveryRules.isEmpty());
            Map<String, JsonNode> nodes = categories.get(category);
            if (nodes == null) {
                nodes = new TreeMap<>();
                categories.put(category, nodes);
            }
            if (nodes.containsKey(layerName)) {
                // add all dependencies
                System.out.println("OVERRIDEN !!!!! " + layerName);
                JsonNode overriden = nodes.get(layerName);
                ArrayNode deps = (ArrayNode) layer.get("dependencies");
                if (deps != null) {
                    ArrayNode overridenDeps = (ArrayNode) overriden.get("dependencies");
                    deps.addAll(overridenDeps);
                }
            }
            nodes.put(layerName, layer);
        }
    }

    private static boolean isInternalLayer(JsonNode layer) {
        ArrayNode props = ((ArrayNode) layer.get("properties"));
        if (props == null) {
            return true;
        }
        Iterator<JsonNode> properties = props.elements();
        while (properties.hasNext()) {
            ObjectNode prop = (ObjectNode) properties.next();
            String name = prop.get("name").asText();
            if (name.equals("org.wildfly.category")) {
                return false;
            }
        }
        return true;
    }

    private static void setRuleDescription(String name, Properties props, ObjectNode node) {
        for (String k : props.stringPropertyNames()) {
            if (name.startsWith(k)) {
                node.put("ruleDescription", props.getProperty(k));
                node.put("valueDescription", props.getProperty(k + ".value"));
                break;
            }
        }
    }
}
