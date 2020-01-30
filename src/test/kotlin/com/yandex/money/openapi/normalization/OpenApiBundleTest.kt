package com.yandex.money.openapi.normalization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.fge.jackson.jsonpointer.JsonPointer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.apache.commons.io.IOUtils
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class OpenApiBundleTest {

    @Rule
    @JvmField
    val wireMockRule = WireMockRule(options().port(8888))

    companion object {
        val mapper = ObjectMapper(YAMLFactory())
    }

    @Test
    fun should_success_bundle() {
        wireMockRule.stubFor(get(urlEqualTo("/domain.yml"))
            .willReturn(aResponse().withBody(IOUtils.toString(this.javaClass.getResource("stubs/Domain.yml")))))

        val fileName = this.javaClass.getResource("test-success/specification.yml")
        val bundledSpecification = OpenApiBundle(fileName.toURI()).bundle().bundledSpecification
        val bundleTree = mapper.readTree(bundledSpecification)

        val expectedResultFileName = this.javaClass.getResource("test-success/specification_expected_result.yml")
        val expectedTree = mapper.readTree(IOUtils.toString(expectedResultFileName))
        Assert.assertEquals(bundleTree, expectedTree)
    }

    @Test
    fun should_get_errors() {
        wireMockRule.stubFor(get(urlEqualTo("/domain.yml"))
            .willReturn(aResponse().withBody(IOUtils.toString(this.javaClass.getResource("stubs/Domain.yml")))))

        val fileName = this.javaClass.getResource("test-conflicts/specification_with_conflicts.yml")
        val conflictingTypeNames = OpenApiBundle(fileName.toURI()).bundle().conflictingTypeNames
        Assert.assertTrue(conflictingTypeNames[JsonPointer.of("components", "responses", "TechnicalError")]?.first().toString().endsWith(
            "domain/Domain.yml#/components/responses/TechnicalError"))
        Assert.assertTrue(conflictingTypeNames[JsonPointer.of("components", "schemas", "PermissionsError")]?.first().toString().endsWith(
            "domain/Domain.yml#/components/schemas/PermissionsError"))
    }
}