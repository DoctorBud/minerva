This is a quick overview on how to setup a Java server for the MolecularModelManager (Minerva).

Pre-Requisites to build the code:
 * Java (JDK 1.7 or later) as compiler
 * Maven (3.0.x) Build-Tool

Build the code:

```
 ./build-server.sh
```

Pre-Requisites to run the server
 * go-lego.owl (GO-SVN/trunk/ontology/extension/go-lego.owl) and catalog.xml to local copies
 * folder with model files (GO-SVN/trunk/experimental/lego/server/owl-models/)

Start the MolecularModelManager server
 * Build the code, will result in a jar
 * Check memory settings in start-m3-server.sh, change as needed.
 * The start script is in the bin folder: start-m3-server.sh

The Minerva server expects parameters for:

```
  -g path-to/go-lego.owl
  -f path-to/owl-models
  [--port 6800]
  [-c path-to/catalog.xml]
```

For more details and options, please check the source code of owltools.gaf.lego.server.StartUpTool

Full example using a catalog.xml, IRIs and assumes a full GO-SVN trunk checkout:

```
start-m3-server.sh -c go-trunk/ontology/extensions/catalog-v001.xml \
-g http://purl.obolibrary.org/obo/go/extensions/go-lego \
-f go-trunk/experimental/lego/server/owl-models \
--port 6800
```

## Alternative for developers:

 * Requires all data (go and models)
 * Build in eclipse, start as main with appropriate parameters.

