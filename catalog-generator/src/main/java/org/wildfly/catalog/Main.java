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
import java.io.FileInputStream;
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
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {
        String wildflyVersion = System.getProperty("wildfly-version");
        if (wildflyVersion == null) {
            throw new Exception("-Dwildfly-version=<version> must be set");
        }
        Path rootDirectory = Paths.get("../docs");
        Path targetDirectory = rootDirectory.resolve(wildflyVersion).toAbsolutePath();
        Files.createDirectories(targetDirectory);
        try (InputStream stream = Main.class.getResourceAsStream("wildfly-catalog.json")) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(stream);
            ObjectNode target = mapper.createObjectNode();
            target.put("description", "Catalog of WildFly " + wildflyVersion + " features (incomplete for now) to provision a WildFly server");
            target.set("documentation", node.get("documentation"));
            target.set("legend", node.get("legend"));
            ArrayNode an = (ArrayNode) node.get("content");
            Iterator<JsonNode> it = an.elements();
            Map<String, Map<String, JsonNode>> categories = new TreeMap<>();
            while (it.hasNext()) {
                JsonNode remoteMetadata = it.next();
                String metadataUrl = remoteMetadata.get("url").asText();
                JsonNode subCatalogMetadata = mapper.readTree(new URL(metadataUrl));
                ArrayNode wildflyVersions = (ArrayNode) subCatalogMetadata.get("wildfly-versions");
                Iterator<JsonNode> versions = wildflyVersions.elements();
                String url = null;
                while (versions.hasNext()) {
                    JsonNode wfVersionNode = versions.next();
                    String vers = wfVersionNode.get("wildfly-version").asText();
                    if (vers.equals(wildflyVersion)) {
                        url = wfVersionNode.get("url").asText();
                        break;
                    }
                }
                if (url == null) {
                    // Do we try with the latest one defined in the latest version?
                    System.err.println("Skipping " + remoteMetadata.get("description") + " no matching metadata for version " + wildflyVersion);
                    continue;
                }
                JsonNode subCatalog = mapper.readTree(new URL(url));
                String fp = subCatalog.get("feature-pack-location").asText();

                ArrayNode layersArray = (ArrayNode) subCatalog.get("layers");
                Iterator<JsonNode> layers = layersArray.elements();
                while (layers.hasNext()) {
                    ObjectNode layer = (ObjectNode) layers.next();
                    layer.put("feature-pack", fp);
                    String layerName = layer.get("name").asText();
                    Iterator<JsonNode> properties = ((ArrayNode) layer.get("properties")).elements();
                    String category = null;
                    String description = null;
                    String note = null;
                    String addOn = null;
                    String stability = null;
                    List<JsonNode> discoveryRules = new ArrayList<>();
                    while (properties.hasNext()) {
                        JsonNode prop = properties.next();
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
                            }
                            continue;
                        }
                        if (name.startsWith("org.wildfly.rule") && !name.startsWith("org.wildfly.rule.add-on")) {
                            discoveryRules.add(prop);
                            continue;
                        }
                    }
                    layer.remove("properties");
                    if (category == null) {
                        throw new Exception("Invalid format, org.wildfly.category is missing for layer " + layer.get("name"));
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
                        ArrayNode overridenDeps = (ArrayNode) overriden.get("dependencies");
                        deps.addAll(overridenDeps);
                    }
                    nodes.put(layerName, layer);
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
            Path json = targetDirectory.resolve("wildfly-catalog.json");
            Files.deleteIfExists(json);
            mapper.writerWithDefaultPrettyPrinter().writeValue(json.toFile(), target);
        }
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
    }

}
