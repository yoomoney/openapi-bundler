package ru.yoomoney.openapi.bundler;

import java.net.URI;

public class Application {
    public static void main(String[] args) {
        URI specificationURI = URI.create("file:path/to/openapi/specification.yaml");

        OpenApiV3SpecificationBundle bundler = new OpenApiV3SpecificationBundle(specificationURI);
        OpenApiV3SpecificationBundle.Result result = bundler.bundle();

        System.out.println("Error: " + result.getConflictingTypeNames());
        System.out.println("Bundled specification: " + result.getBundledSpecification());
    }
}
