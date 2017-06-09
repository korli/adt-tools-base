<?xml version="1.0"?>
<globals>
    <global id="topOut" value="." />
    <global id="projectOut" value="." />
    <global id="manifestOut" value="${manifestDir}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="testOut" value="androidTest/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="buildToolsVersion" value="19.1.0" />
    <global id="gradlePluginVersion" value="0.12.+" />
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
</globals>
