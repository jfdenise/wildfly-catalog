# wildfly-catalog

This catalog is currently incomplete, it attempts to provide a base for discussing its content.

* This catalog relies on a JSON file that contains the data we want to see exposed.
* This JSON file should be built by hand to finely identify what needs to be exposed and how.
* This catalog references some XML server configuration files that should be generated from the catalog json file.
* This catalog should be regenerated for each new WildFly release.
* This catalog approach doesn't require new metadata in Galleon layers.
* A proper workflow and tooling should be put in place to manage the catalog.

## Exposed content

* Only a subset of Galleon layers are exposed in the catalog. Mainly the layer that directly provide a non "core" functionality. 
Core functionalities are functionalities we don't want to see optional when a layer is provisioned. For example: elytron, transactions are not exposed. 
They are automatically provisioned by layers that depend on them.
* All the feature-packs that are located in the [WildFly galleon feature-packs repository](https://github.com/wildfly/wildfly-galleon-feature-packs) should be covered.
* For WildFly feature-packs, the wildfly-ee-galleon-pack is not exposed, its content being contained in the wildfly-galleon-pack (with some possible adjustments).

## Searching the catalog

* This POC proposes a search feature based on free text.

## Functionalities categories

* Layers are grouped into categories to help navigation.

## Exposed Glow information

* Discoverability and addOn (if the layer is not discoverable) are exposed.
* Glow rules are not exposed, they are exposed already in the Glow documentation.

## Stability level

* Stability level of a layer is exposed.

## Produced XML configuration

* The XML produced when provisioning a layer is exposed. This seems an interesting information to understand what a layer is actually contributing to the configuration.

## Cloud exposed information

* Layers that are automatically provisioned for the cloud (health and core-tools) are advertised.
* The XML produced in a cloud context (when the cloud FP is in use) is exposed.

## Open questions

* Should we split the catalog into multiple JSON files then aggregated in a main catalog?
** Split the catalog per feature-pack and have some logic to aggregate the content in each category?
** Split only for incubating features.

## Updating the catalog possible  workflow

* When a new layer is defined, an entry must be added to the catalog with all required information.
** Some tooling is used to validate the catalog and generate the XML server configurations that this new layer contributes.
* When a layer is removed (ultra rare-case).
** Some tooling is used to properly remove the layer from the catalog.
* For each new WildFly Major release, 
** Some tooling is used to update the versions based on the [WildFly galleon feature-packs repository](https://github.com/wildfly/wildfly-galleon-feature-packs).
** Some tooling is used to validate that the catalog contains only valid layers and that dependencies are correct.

## Envisioned tooling

* A command line named `wildfly-catalog` could be defined to handle the various updates and releasing.
