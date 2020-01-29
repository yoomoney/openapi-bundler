package com.yandex.money.openapi.normalization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.apache.commons.io.IOUtils
import org.junit.Assert
import org.junit.Test

class OpenApiBundlerTest {

    companion object {
        val mapper = ObjectMapper(YAMLFactory())
    }

    @Test
    fun should_success_bundle() {
        val fileName = this.javaClass.getResource("test-normalization.yml")
        val bundle = OpenApiBundler().bundle(fileName.toURI())
        val bundleTree = mapper.readTree(bundle)

        val expectedResultFileName = this.javaClass.getResource("test-normalization_expected_result.yml")
        val expectedTree = mapper.readTree(IOUtils.toString(expectedResultFileName))
        Assert.assertEquals(bundleTree, expectedTree);
    }


}