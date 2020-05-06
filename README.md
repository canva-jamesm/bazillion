# Bazillion
[![Build Status](https://travis-ci.org/SerCeMan/bazillion.svg?branch=master)](https://travis-ci.org/SerCeMan/bazillion)

Bazillion is an opinionated alternative Bazel plugin for IntelliJ IDEA.

## Why

To allow for opening of any kind of project, the [official bazel plugin](https://github.com/bazelbuild/intellij) needs to call bazel to get the required information which means that the information is always correct, but unfortunately these calls are often costly on large projects. 

Bazillion chooses the other side of the tradeoff and builds the IntelliJ project structure by parsing the BUILD files directly. Bazillion also provides a first-class support for [external dependencies](https://github.com/bazelbuild/rules_jvm_external). 

## Requirements

* The files in each module should be places according to the [standard directory layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html).
* [external dependencies](https://github.com/bazelbuild/rules_jvm_external) should be used as the 3rd party dependency provider.  

## Installation

The plugin can be installed from a custom IJ plugin repository. To install the plugin, you can follow [these instructions](https://www.jetbrains.com/help/idea/managing-plugins.html#repos). 

Repository URL:
```
https://raw.githubusercontent.com/SerCeMan/bazillion/master/updatePlugins.xml
```

## Project Loading

The load your project using Bazillion, you'll need to open an existing project or create a new one so you can see the "File" menu. The welcome screen doesn't have the functionality to load a Bazil project. Once you've done that, open a project from existing sources

![image](https://user-images.githubusercontent.com/55791582/81141004-a0053a00-8fae-11ea-9b21-31c7c184de4b.png)

Then navigate to the canva/canva repository and click "Open". Afterwards, select "Bazil" from the possible configurations and "Import".

![image](https://user-images.githubusercontent.com/55791582/81141299-8e706200-8faf-11ea-8ce0-c7c650f195f6.png)

Then you just have to wait for the initial download and indexing and you're good to go!

## Current status

This plugin is in alpha stage.

## Contribute

Contributions are always welcome!
