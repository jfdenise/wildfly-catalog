# wildfly-catalog

* This catalog relies on a JSON file that contains the data we want to see exposed.
* It JSON file should be built by hand to finely identify what needs to be exposed and how.
* This catalog references some XML server configuration files that should be generated from the catalog json file.

## Exposed content

* A subset of galleon layers are exposed in the catalog. Mainly the layer that directly provide a non "core" functionality. 
Core functionalities are functionalities we don't want to see optional when a layer is provisioned. For example: elytron, transactions are not exposed. 
They are automatically provisioned by layers that depend on them. 

## Functionalities categories

* Layers are grouped into categories to help navigation.

## Exposed Glow information

* Discoverability and addOn (if the layer is not discoverable) are exposed.
* Glow rules are not exposed. This could be revisited, they seem quite low level.

## Stability level

* Stability level of a layer is exposed.

## Produced XML configuration

* The XML produced when provisioning a layer is exposed. This seems an interesting information to understand what a layer is actually contributing to the configuration.

## Cloud exposed information

* If a layer is cloud specific (didn't find some yet, ...) the layer is marked as so.
* The XML produced in a cloud context (when the cloud FP is in use) is exposed.


