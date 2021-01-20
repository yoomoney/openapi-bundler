package com.yandex.money.openapi.normalization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.apache.commons.io.IOUtils
import org.hamcrest.core.StringEndsWith
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class OpenApiV3SpecificationBundleTest {

    @Rule
    @JvmField
    val wireMockRule = WireMockRule(options().port(8888))

    companion object {
        val mapper = ObjectMapper(YAMLFactory())
    }

    @Test
    fun should_success_bundle() {
        wireMockRule.stubFor(get(urlEqualTo("/domain.yaml"))
            .willReturn(aResponse().withBody(IOUtils.toString(this.javaClass.getResource("stubs/Domain.yaml")))))

        val fileName = this.javaClass.getResource("test-success/specification.yaml")
        val bundledSpecification = OpenApiV3SpecificationBundle(fileName.toURI()).bundle().bundledSpecification
        val bundleTree = mapper.readTree(bundledSpecification)

        val expectedResultFileName = this.javaClass.getResource("test-success/specification_expected_result.yaml")
        val expectedTree = mapper.readTree(IOUtils.toString(expectedResultFileName))
        Assert.assertEquals(bundleTree, expectedTree)
    }

    @Test
    fun should_success_bundle_with_paths_ref() {
        wireMockRule.stubFor(get(urlEqualTo("/domain.yaml"))
            .willReturn(aResponse().withBody(IOUtils.toString(this.javaClass.getResource("stubs/Domain.yaml")))))

        val fileName = this.javaClass.getResource("test-success-with-paths-ref/specification.yaml")
        val bundledSpecification = OpenApiV3SpecificationBundle(fileName.toURI()).bundle().bundledSpecification
        val bundleTree = mapper.readTree(bundledSpecification)

        val expectedResultFileName = this.javaClass.getResource("test-success-with-paths-ref/specification_expected_result.yaml")
        val expectedTree = mapper.readTree(IOUtils.toString(expectedResultFileName))
        Assert.assertEquals(bundleTree, expectedTree)
    }

    @Test
    fun should_success_bundle_with_api_type_library_refs() {
        wireMockRule.stubFor(get(urlEqualTo("/domain.yaml"))
            .willReturn(aResponse().withBody(IOUtils.toString(this.javaClass.getResource("stubs/Domain.yaml")))))

        val fileName = this.javaClass.getResource("test-success-with-api-type-library-refs/specification.yaml")
        val bundledSpecification = OpenApiV3SpecificationBundle(fileName.toURI()).bundle().bundledSpecification
        val bundleTree = mapper.readTree(bundledSpecification)

        val expectedResultFileName = this.javaClass.getResource("test-success-with-api-type-library-refs/specification_expected_result.yaml")
        val expectedTree = mapper.readTree(IOUtils.toString(expectedResultFileName))
        Assert.assertEquals(bundleTree, expectedTree)
    }

    @Test
    fun should_get_errors() {
        wireMockRule.stubFor(get(urlEqualTo("/domain.yaml"))
            .willReturn(aResponse().withBody(IOUtils.toString(this.javaClass.getResource("stubs/Domain.yaml")))))

        val fileName = this.javaClass.getResource("test-conflicts/specification_with_conflicts.yaml")
        val conflictingTypeNames = OpenApiV3SpecificationBundle(fileName.toURI()).bundle().conflictingTypeNames

        Assert.assertThat(conflictingTypeNames["/components/responses/TechnicalError"]!!.toList().get(0).toASCIIString(),
            StringEndsWith.endsWith("specification_with_conflicts.yaml#"))
        Assert.assertThat(conflictingTypeNames["/components/responses/TechnicalError"]!!.toList().get(1).toASCIIString(),
            StringEndsWith.endsWith("domain/Domain.yaml#"))

        Assert.assertThat(conflictingTypeNames["/components/schemas/PermissionsError"]!!.toList()[0].toASCIIString(),
            StringEndsWith.endsWith("specification_with_conflicts.yaml#"))
        Assert.assertThat(conflictingTypeNames["/components/schemas/PermissionsError"]!!.toList()[1].toASCIIString(),
            StringEndsWith.endsWith("domain/Domain.yaml#"))
    }
}