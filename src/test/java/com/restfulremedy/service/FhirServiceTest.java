package com.restfulremedy.service;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FhirServiceTest {

    private FhirService fhirService;

    @BeforeEach
    void setUp() {
        fhirService = new FhirService();
    }

    @Test
    void buildBundle_fullResponse_containsAllResources() {
        Map<String, Object> claudeResponse = Map.of(
                "summary", "Follow-up visit for hypertension management",
                "medications", List.of(
                        Map.of("name", "Lisinopril", "dosage", "10mg", "rxnorm_code", "104377")
                ),
                "diagnoses", List.of(
                        Map.of("description", "Essential hypertension", "icd10_code", "I10")
                )
        );

        Bundle bundle = fhirService.buildBundle(claudeResponse);

        assertEquals(Bundle.BundleType.COLLECTION, bundle.getType());
        // 1 Composition + 1 MedicationStatement + 1 Condition = 3
        assertEquals(3, bundle.getEntry().size());
    }

    @Test
    void buildBundle_medicationHasRxNormCoding() {
        Map<String, Object> claudeResponse = Map.of(
                "medications", List.of(
                        Map.of("name", "Lisinopril", "dosage", "10mg", "rxnorm_code", "104377")
                ),
                "diagnoses", List.of()
        );

        Bundle bundle = fhirService.buildBundle(claudeResponse);

        MedicationStatement med = (MedicationStatement) bundle.getEntry().get(0).getResource();
        CodeableConcept medicationCode = med.getMedicationCodeableConcept();
        assertEquals("Lisinopril", medicationCode.getText());
        assertEquals("http://www.nlm.nih.gov/research/umls/rxnorm",
                medicationCode.getCodingFirstRep().getSystem());
        assertEquals("104377", medicationCode.getCodingFirstRep().getCode());
    }

    @Test
    void buildBundle_conditionHasIcd10Coding() {
        Map<String, Object> claudeResponse = Map.of(
                "medications", List.of(),
                "diagnoses", List.of(
                        Map.of("description", "Essential hypertension", "icd10_code", "I10")
                )
        );

        Bundle bundle = fhirService.buildBundle(claudeResponse);

        Condition condition = (Condition) bundle.getEntry().get(0).getResource();
        assertEquals("Essential hypertension", condition.getCode().getText());
        assertEquals("http://hl7.org/fhir/sid/icd-10-cm",
                condition.getCode().getCodingFirstRep().getSystem());
        assertEquals("I10", condition.getCode().getCodingFirstRep().getCode());
    }

    @Test
    void buildBundle_multipleMedicationsAndDiagnoses() {
        Map<String, Object> claudeResponse = Map.of(
                "medications", List.of(
                        Map.of("name", "Lisinopril", "dosage", "10mg", "rxnorm_code", "104377"),
                        Map.of("name", "Metformin", "dosage", "500mg", "rxnorm_code", "861004")
                ),
                "diagnoses", List.of(
                        Map.of("description", "Hypertension", "icd10_code", "I10"),
                        Map.of("description", "Type 2 diabetes", "icd10_code", "E11.9")
                )
        );

        Bundle bundle = fhirService.buildBundle(claudeResponse);

        // 2 medications + 2 diagnoses = 4 entries (no summary key provided)
        assertEquals(4, bundle.getEntry().size());
    }

    @Test
    void buildBundle_emptyLists_returnsEmptyBundle() {
        Map<String, Object> claudeResponse = Map.of(
                "medications", List.of(),
                "diagnoses", List.of()
        );

        Bundle bundle = fhirService.buildBundle(claudeResponse);

        assertEquals(0, bundle.getEntry().size());
        assertEquals(Bundle.BundleType.COLLECTION, bundle.getType());
    }

    @Test
    void serializeBundle_producesValidJson() {
        Map<String, Object> claudeResponse = Map.of(
                "summary", "Routine checkup",
                "medications", List.of(
                        Map.of("name", "Aspirin", "dosage", "81mg", "rxnorm_code", "243670")
                ),
                "diagnoses", List.of()
        );

        Bundle bundle = fhirService.buildBundle(claudeResponse);
        String json = fhirService.serializeBundle(bundle);

        assertNotNull(json);
        assertTrue(json.contains("\"resourceType\": \"Bundle\""));
        assertTrue(json.contains("\"resourceType\": \"MedicationStatement\""));
        assertTrue(json.contains("243670"));
    }

    @Test
    void buildBundle_medicationWithoutDosage_noDosageAdded() {
        Map<String, Object> claudeResponse = Map.of(
                "medications", List.of(
                        Map.of("name", "Aspirin", "rxnorm_code", "243670")
                ),
                "diagnoses", List.of()
        );

        Bundle bundle = fhirService.buildBundle(claudeResponse);

        MedicationStatement med = (MedicationStatement) bundle.getEntry().get(0).getResource();
        assertTrue(med.getDosage().isEmpty());
    }
}
