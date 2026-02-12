# Publishing jb

The following JBuild projects are publishable:

* jbuild-api
* jbuild-classfile-parser
* jbuild

The `publish` extension task can be used to publish all three.

## Preparation

Go to https://central.sonatype.com/usertoken and generate a user token.

Save the XML snippet created by Sonatype in the file `.publish-token.xml`.

## Publish

Now, run:

```shell
jb publish
```
