package com.yandex.money.openapi.normalization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.apache.commons.io.IOUtils
import org.hamcrest.core.IsEqual
import org.hamcrest.core.StringEndsWith.endsWith
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class OpenApiV3LibraryBundleTest {

    @Rule
    @JvmField
    val wireMockRule = WireMockRule(options().port(8888))

    companion object {
        val mapper = ObjectMapper(YAMLFactory())
    }

    @Test
    fun should_success_bundle() {
        wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/domain.yaml"))
            .willReturn(WireMock.aResponse().withBody(IOUtils.toString(this.javaClass.getResource("stubs/Domain.yaml")))))

        val fileNames = arrayOf(
            this.javaClass.getResource("library-success/domain-language.yaml").toURI(),
            this.javaClass.getResource("library-success/domain-phone.yaml").toURI(),
            this.javaClass.getResource("library-success/domain-useragent.yaml").toURI(),
            this.javaClass.getResource("library-success/domain-pagination.yaml").toURI(),
            this.javaClass.getResource("library-success/domain-monetary.yaml").toURI(),
            this.javaClass.getResource("library-success/domain-card.yaml").toURI(),
            this.javaClass.getResource("library-success/domain-caching.yaml").toURI(),
            this.javaClass.getResource("library-success/domain-pagination.yaml").toURI(),
            this.javaClass.getResource("library-success/common-specification.yaml").toURI()
        )
        val bundledSpecification = OpenApiV3LibraryBundle(fileNames).bundle().bundledSpecification
        val bundleTree = mapper.readTree(bundledSpecification)

        val expectedResultFileName = this.javaClass.getResource("library-success/result.yaml")
        val expectedTree = mapper.readTree(IOUtils.toString(expectedResultFileName))
        Assert.assertEquals(bundleTree, expectedTree)
    }

    @Test
    fun should_get_errors() {
        wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/domain.yaml"))
            .willReturn(WireMock.aResponse().withBody(IOUtils.toString(this.javaClass.getResource("stubs/Domain.yaml")))))

        val fileNames = arrayOf(
            this.javaClass.getResource("library-conflicts/domain-language.yaml").toURI(),
            this.javaClass.getResource("library-conflicts/domain-phone.yaml").toURI(),
            this.javaClass.getResource("library-conflicts/domain-useragent.yaml").toURI(),
            this.javaClass.getResource("library-conflicts/domain-pagination.yaml").toURI(),
            this.javaClass.getResource("library-conflicts/domain-monetary.yaml").toURI(),
            this.javaClass.getResource("library-conflicts/domain-card.yaml").toURI(),
            this.javaClass.getResource("library-conflicts/domain-caching.yaml").toURI(),
            this.javaClass.getResource("library-conflicts/domain-pagination.yaml").toURI(),
            this.javaClass.getResource("library-conflicts/common-specification.yaml").toURI()
        )

        val conflictingTypeNames = OpenApiV3LibraryBundle(fileNames).bundle().conflictingTypeNames
        Assert.assertThat(conflictingTypeNames["/components/schemas/Number"]!!.toList().get(0).toASCIIString(),
            endsWith("library-conflicts/domain-phone.yaml#"))
        Assert.assertThat(conflictingTypeNames["/components/schemas/Number"]!!.toList().get(1).toASCIIString(),
            endsWith("library-conflicts/domain-card.yaml#"))

        Assert.assertThat(conflictingTypeNames["/components/schemas/CurrencyCode"]!!.toList()[0].toASCIIString(),
            endsWith("library-conflicts/domain-monetary.yaml#"))
        Assert.assertThat(conflictingTypeNames["/components/schemas/CurrencyCode"]!!.toList()[1].toASCIIString(),
            IsEqual.equalTo("http://localhost:8888/domain.yaml#"))

        Assert.assertThat(conflictingTypeNames["/components/schemas/Expires"]!!.toList().get(0).toASCIIString(),
            endsWith("library-conflicts/domain-card.yaml#"))
        Assert.assertThat(conflictingTypeNames["/components/schemas/Expires"]!!.toList().get(1).toASCIIString(),
            endsWith("library-conflicts/domain-caching.yaml#"))
    }
}