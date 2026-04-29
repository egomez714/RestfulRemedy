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
    void buildBundle_alwaysIncludesPatientAndEncounter() {
        Bundle bundle = fhirService.buildBundle(Map.of(
                "medications", List.of(),
                "diagnoses", List.of(),
                "observations", List.of()
        ));

        assertEquals(Bundle.BundleType.COLLECTION, bundle.getType());
        assertTrue(findResource(bundle, Patient.class).isPresent(),
                "Bundle should always contain a Patient");
        assertTrue(findResource(bundle, Encounter.class).isPresent(),
                "Bundle should always contain an Encounter");
    }

    @Test
    void buildBundle_fullResponse_containsAllResourceTypes() {
        Map<String, Object> claudeResponse = Map.of(
                "patient", Map.of("name", "Jane Doe", "age", 45, "gender", "female"),
                "encounter", Map.of("type", "office visit", "reason", "follow-up"),
                "summary", "Follow-up visit for hypertension management",
                "medications", List.of(
                        Map.of("name", "Lisinopril", "dosage", "10mg", "rxnorm_code", "104377")
                ),
                "diagnoses", List.of(
                        Map.of("description", "Essential hypertension", "icd10_code", "I10")
                ),
                "observations", List.of(
                        Map.of("description", "Blood pressure", "value", "130/85 mmHg", "loinc_code", "85354-9")
                )
        );

        Bundle bundle = fhirService.buildBundle(claudeResponse);

        // Patient + Encounter + Composition + MedicationStatement + Condition + Observation = 6
        assertEquals(6, bundle.getEntry().size());
        assertTrue(findResource(bundle, Patient.class).isPresent());
        assertTrue(findResource(bundle, Encounter.class).isPresent());
        assertTrue(findResource(bundle, Composition.class).isPresent());
        assertTrue(findResource(bundle, MedicationStatement.class).isPresent());
        assertTrue(findResource(bundle, Condition.class).isPresent());
        assertTrue(findResource(bundle, Observation.class).isPresent());
    }

    @Test
    void buildBundle_clinicalResourcesReferencePatient() {
        Map<String, Object> claudeResponse = Map.of(
                "medications", List.of(
                        Map.of("name", "Aspirin", "rxnorm_code", "243670")
                ),
                "diagnoses", List.of(
                        Map.of("description", "Hypertension", "icd10_code", "I10")
                ),
                "observations", List.of()
        );

        Bundle bundle = fhirService.buildBundle(claudeResponse);
        Patient patient = findResource(bundle, Patient.class).orElseThrow();
        String patientFullUrl = "urn:uuid:" + patient.getIdElement().getIdPart();

        MedicationStatement med = findResource(bundle, MedicationStatement.class).orElseThrow();
        Condition condition = findResource(bundle, Condition.class).orElseThrow();

        assertEquals(patientFullUrl, med.getSubject().getReference());
        assertEquals(patientFullUrl, condition.getSubject().getReference());
    }

    @Test
    void buildBundle_clinicalResourcesReferenceEncounter() {
        Map<String, Object> claudeResponse = Map.of(
                "medications", List.of(
                        Map.of("name", "Aspirin", "rxnorm_code", "243670")
                ),
                "diagnoses", List.of(
                        Map.of("description", "Hypertension", "icd10_code", "I10")
                ),
                "observations", List.of()
        );

        Bundle bundle = fhirService.buildBundle(claudeResponse);
        Encounter encounter = findResource(bundle, Encounter.class).orElseThrow();
        String encounterFullUrl = "urn:uuid:" + encounter.getIdElement().getIdPart();

        MedicationStatement med = findResource(bundle, MedicationStatement.class).orElseThrow();
        Condition condition = findResource(bundle, Condition.class).orElseThrow();

        assertEquals(encounterFullUrl, med.getContext().getReference());
        assertEquals(encounterFullUrl, condition.getEncounter().getReference());
    }

    @Test
    void buildBundle_medicationHasRxNormCoding() {
        Bundle bundle = fhirService.buildBundle(Map.of(
                "medications", List.of(
                        Map.of("name", "Lisinopril", "dosage", "10mg", "rxnorm_code", "104377")
                ),
                "diagnoses", List.of(),
                "observations", List.of()
        ));

        MedicationStatement med = findResource(bundle, MedicationStatement.class).orElseThrow();
        CodeableConcept medicationCode = med.getMedicationCodeableConcept();
        assertEquals("Lisinopril", medicationCode.getText());
        assertEquals("http://www.nlm.nih.gov/research/umls/rxnorm",
                medicationCode.getCodingFirstRep().getSystem());
        assertEquals("104377", medicationCode.getCodingFirstRep().getCode());
    }

    @Test
    void buildBundle_conditionHasIcd10Coding() {
        Bundle bundle = fhirService.buildBundle(Map.of(
                "medications", List.of(),
                "diagnoses", List.of(
                        Map.of("description", "Essential hypertension", "icd10_code", "I10")
                ),
                "observations", List.of()
        ));

        Condition condition = findResource(bundle, Condition.class).orElseThrow();
        assertEquals("Essential hypertension", condition.getCode().getText());
        assertEquals("http://hl7.org/fhir/sid/icd-10-cm",
                condition.getCode().getCodingFirstRep().getSystem());
        assertEquals("I10", condition.getCode().getCodingFirstRep().getCode());
    }

    @Test
    void buildBundle_observationHasLoincCoding() {
        Bundle bundle = fhirService.buildBundle(Map.of(
                "medications", List.of(),
                "diagnoses", List.of(),
                "observations", List.of(
                        Map.of("description", "Heart rate", "value", "72 bpm", "loinc_code", "8867-4")
                )
        ));

        Observation observation = findResource(bundle, Observation.class).orElseThrow();
        assertEquals("Heart rate", observation.getCode().getText());
        assertEquals("http://loinc.org", observation.getCode().getCodingFirstRep().getSystem());
        assertEquals("8867-4", observation.getCode().getCodingFirstRep().getCode());
        assertEquals("72 bpm", observation.getValueStringType().getValue());
    }

    @Test
    void buildBundle_patientPopulatedFromClaudeData() {
        Bundle bundle = fhirService.buildBundle(Map.of(
                "patient", Map.of("name", "John Smith", "gender", "male"),
                "medications", List.of(),
                "diagnoses", List.of(),
                "observations", List.of()
        ));

        Patient patient = findResource(bundle, Patient.class).orElseThrow();
        assertEquals("John Smith", patient.getNameFirstRep().getText());
        assertEquals(Enumerations.AdministrativeGender.MALE, patient.getGender());
    }

    @Test
    void buildBundle_emptyResponse_stillProducesPatientAndEncounter() {
        Bundle bundle = fhirService.buildBundle(Map.of(
                "medications", List.of(),
                "diagnoses", List.of(),
                "observations", List.of()
        ));

        assertEquals(2, bundle.getEntry().size());
        assertEquals(Bundle.BundleType.COLLECTION, bundle.getType());
    }

    @Test
    void buildBundle_entriesHaveUrnUuidFullUrls() {
        Bundle bundle = fhirService.buildBundle(Map.of(
                "medications", List.of(),
                "diagnoses", List.of(),
                "observations", List.of()
        ));

        bundle.getEntry().forEach(entry ->
                assertTrue(entry.getFullUrl().startsWith("urn:uuid:"),
                        "Each entry should have a urn:uuid fullUrl, got: " + entry.getFullUrl()));
    }

    @Test
    void serializeBundle_producesValidJson() {
        Bundle bundle = fhirService.buildBundle(Map.of(
                "summary", "Routine checkup",
                "medications", List.of(
                        Map.of("name", "Aspirin", "dosage", "81mg", "rxnorm_code", "243670")
                ),
                "diagnoses", List.of(),
                "observations", List.of()
        ));
        String json = fhirService.serializeBundle(bundle);

        assertNotNull(json);
        assertTrue(json.contains("\"resourceType\": \"Bundle\""));
        assertTrue(json.contains("\"resourceType\": \"MedicationStatement\""));
        assertTrue(json.contains("\"resourceType\": \"Patient\""));
        assertTrue(json.contains("\"resourceType\": \"Encounter\""));
        assertTrue(json.contains("243670"));
    }

    @Test
    void buildBundle_medicationWithoutDosage_noDosageAdded() {
        Bundle bundle = fhirService.buildBundle(Map.of(
                "medications", List.of(
                        Map.of("name", "Aspirin", "rxnorm_code", "243670")
                ),
                "diagnoses", List.of(),
                "observations", List.of()
        ));

        MedicationStatement med = findResource(bundle, MedicationStatement.class).orElseThrow();
        assertTrue(med.getDosage().isEmpty());
    }

    private <T extends Resource> java.util.Optional<T> findResource(Bundle bundle, Class<T> type) {
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }
}
