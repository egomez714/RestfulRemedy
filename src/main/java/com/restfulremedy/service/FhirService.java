package com.restfulremedy.service;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts Claude's structured JSON output into a valid FHIR R4 Bundle
 * containing MedicationStatement and Condition resources.
 */
@Service
@Slf4j
public class FhirService {

    // FhirContext is expensive to create — reuse a single instance.
    // It knows how to serialize/deserialize FHIR R4 resources.
    private final FhirContext fhirContext = FhirContext.forR4();

    /**
     * Takes the Map that Claude returned and builds a FHIR Bundle.
     *
     * A Bundle is like a folder — it groups related FHIR resources together.
     * We use a "collection" bundle type, which simply means "here are some
     * resources that belong together."
     */
    public Bundle buildBundle(Map<String, Object> claudeResponse) {
        log.info("Converting Claude response to FHIR Bundle");

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.setTimestamp(new Date());
        bundle.setId(UUID.randomUUID().toString());

        // Add the visit summary as a basic Composition resource
        String summary = (String) claudeResponse.get("summary");
        if (summary != null) {
            bundle.addEntry().setResource(buildComposition(summary));
        }

        // Convert each medication into a FHIR MedicationStatement
        List<Map<String, Object>> medications =
                (List<Map<String, Object>>) claudeResponse.get("medications");
        if (medications != null) {
            for (Map<String, Object> med : medications) {
                bundle.addEntry().setResource(buildMedicationStatement(med));
            }
        }

        // Convert each diagnosis into a FHIR Condition
        List<Map<String, Object>> diagnoses =
                (List<Map<String, Object>>) claudeResponse.get("diagnoses");
        if (diagnoses != null) {
            for (Map<String, Object> dx : diagnoses) {
                bundle.addEntry().setResource(buildCondition(dx));
            }
        }

        log.info("Built FHIR Bundle with {} entries", bundle.getEntry().size());
        return bundle;
    }

    /**
     * Builds a Composition resource to hold the visit summary.
     *
     * Composition is FHIR's way of representing a clinical document or note.
     * We use it here to carry Claude's one-sentence summary of the visit.
     */
    private Composition buildComposition(String summary) {
        Composition composition = new Composition();
        composition.setId(UUID.randomUUID().toString());
        composition.setStatus(Composition.CompositionStatus.FINAL);
        composition.setDate(new Date());
        composition.setTitle("Visit Summary");

        // The "type" tells other systems what kind of document this is.
        // LOINC 51847-2 = "Evaluation + Plan note"
        composition.setType(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("51847-2")
                        .setDisplay("Evaluation + Plan note")));

        // Add the summary text in a section
        Composition.SectionComponent section = new Composition.SectionComponent();
        section.setTitle("Summary");
        Narrative narrative = new Narrative();
        narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
        narrative.setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\">" + summary + "</div>");
        section.setText(narrative);
        composition.addSection(section);

        return composition;
    }

    /**
     * Converts a medication map from Claude into a FHIR MedicationStatement.
     *
     * MedicationStatement represents a record that a patient is taking (or was
     * prescribed) a medication. The key piece is the RxNorm coding — this is
     * how systems universally identify medications.
     *
     * RxNorm system URI: http://www.nlm.nih.gov/research/umls/rxnorm
     */
    private MedicationStatement buildMedicationStatement(Map<String, Object> med) {
        MedicationStatement statement = new MedicationStatement();
        statement.setId(UUID.randomUUID().toString());
        statement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);

        // Build a CodeableConcept with the RxNorm coding
        String name = (String) med.get("name");
        String rxnormCode = (String) med.get("rxnorm_code");

        CodeableConcept medicationCode = new CodeableConcept();
        medicationCode.setText(name);
        if (rxnormCode != null && !rxnormCode.isBlank()) {
            medicationCode.addCoding(new Coding()
                    .setSystem("http://www.nlm.nih.gov/research/umls/rxnorm")
                    .setCode(rxnormCode)
                    .setDisplay(name));
        }
        statement.setMedication(medicationCode);

        // Add dosage if Claude extracted it
        String dosage = (String) med.get("dosage");
        if (dosage != null && !dosage.isBlank()) {
            statement.addDosage(new Dosage().setText(dosage));
        }

        return statement;
    }

    /**
     * Converts a diagnosis map from Claude into a FHIR Condition.
     *
     * Condition represents a clinical condition, problem, or diagnosis.
     * The ICD-10 coding is the international standard for classifying diseases.
     *
     * ICD-10 system URI: http://hl7.org/fhir/sid/icd-10-cm
     */
    private Condition buildCondition(Map<String, Object> dx) {
        Condition condition = new Condition();
        condition.setId(UUID.randomUUID().toString());

        // Clinical status: active (the patient currently has this condition)
        condition.setClinicalStatus(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical")
                        .setCode("active")
                        .setDisplay("Active")));

        // Build a CodeableConcept with the ICD-10 coding
        String description = (String) dx.get("description");
        String icd10Code = (String) dx.get("icd10_code");

        CodeableConcept code = new CodeableConcept();
        code.setText(description);
        if (icd10Code != null && !icd10Code.isBlank()) {
            code.addCoding(new Coding()
                    .setSystem("http://hl7.org/fhir/sid/icd-10-cm")
                    .setCode(icd10Code)
                    .setDisplay(description));
        }
        condition.setCode(code);

        return condition;
    }

    /**
     * Serializes a FHIR Bundle to a JSON string.
     * This is what we'll return from our API endpoint.
     */
    public String serializeBundle(Bundle bundle) {
        return fhirContext.newJsonParser()
                .setPrettyPrint(true)
                .encodeResourceToString(bundle);
    }
}
