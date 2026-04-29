package com.restfulremedy.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts Claude's structured JSON output into a valid FHIR R4 Bundle.
 *
 * Produces: Patient + Encounter + Composition + MedicationStatement(s)
 *           + Condition(s) + Observation(s), all linked via references.
 */
@Service
@Slf4j
public class FhirService {

    // FhirContext is expensive to create — reuse a single instance.
    private final FhirContext fhirContext = FhirContext.forR4();
    private final FhirValidator validator;

    public FhirService() {
        // FhirInstanceValidator runs structural + cardinality + profile checks.
        // Terminology checks (does this RxNorm code actually exist?) require a
        // terminology server — we skip those by leaving terminology validation
        // unwired, so we get a fast structural-only validation pass.
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(fhirContext);
        this.validator = fhirContext.newValidator();
        validator.registerValidatorModule(instanceValidator);
    }

    /**
     * Builds a FHIR Bundle from Claude's extracted clinical data.
     * The returned Bundle uses urn:uuid fullUrls so internal resources
     * can reference each other without needing a server-assigned ID.
     */
    public Bundle buildBundle(Map<String, Object> claudeResponse) {
        log.info("Converting Claude response to FHIR Bundle");

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.setTimestamp(new Date());
        bundle.setId(UUID.randomUUID().toString());

        // 1. Patient first — everything else references it.
        Map<String, Object> patientData = asMap(claudeResponse.get("patient"));
        Patient patient = buildPatient(patientData);
        String patientFullUrl = addEntry(bundle, patient);
        Reference patientRef = new Reference(patientFullUrl);

        // 2. Encounter — represents the visit itself.
        Map<String, Object> encounterData = asMap(claudeResponse.get("encounter"));
        Encounter encounter = buildEncounter(encounterData, patientRef);
        String encounterFullUrl = addEntry(bundle, encounter);
        Reference encounterRef = new Reference(encounterFullUrl);

        // 3. Composition — the visit summary as a clinical note.
        String summary = (String) claudeResponse.get("summary");
        if (summary != null && !summary.isBlank()) {
            addEntry(bundle, buildComposition(summary, patientRef, encounterRef));
        }

        // 4. Medications.
        for (Map<String, Object> med : asList(claudeResponse.get("medications"))) {
            addEntry(bundle, buildMedicationStatement(med, patientRef, encounterRef));
        }

        // 5. Diagnoses.
        for (Map<String, Object> dx : asList(claudeResponse.get("diagnoses"))) {
            addEntry(bundle, buildCondition(dx, patientRef, encounterRef));
        }

        // 6. Observations (vitals, labs, etc.).
        for (Map<String, Object> obs : asList(claudeResponse.get("observations"))) {
            addEntry(bundle, buildObservation(obs, patientRef, encounterRef));
        }

        log.info("Built FHIR Bundle with {} entries", bundle.getEntry().size());
        return bundle;
    }

    /**
     * Validates a Bundle structurally and logs any issues.
     * Returns the validation result so callers can decide whether to fail.
     * Today we log and continue — terminology issues are expected because we
     * don't run a terminology server.
     */
    public ValidationResult validate(Bundle bundle) {
        ValidationResult result = validator.validateWithResult(bundle);
        if (!result.isSuccessful()) {
            for (SingleValidationMessage msg : result.getMessages()) {
                log.warn("FHIR validation [{}]: {} ({})",
                        msg.getSeverity(), msg.getMessage(), msg.getLocationString());
            }
        } else {
            log.info("FHIR Bundle validated successfully");
        }
        return result;
    }

    public String serializeBundle(Bundle bundle) {
        return fhirContext.newJsonParser()
                .setPrettyPrint(true)
                .encodeResourceToString(bundle);
    }

    // -- resource builders ---------------------------------------------------

    private Patient buildPatient(Map<String, Object> data) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID().toString());

        if (data == null) return patient;

        Object name = data.get("name");
        if (name instanceof String s && !s.isBlank()) {
            patient.addName(new HumanName().setText(s));
        }

        Object age = data.get("age");
        if (age != null) {
            // Convert age in years to an approximate birthDate (Jan 1).
            try {
                int years = (age instanceof Number n) ? n.intValue() : Integer.parseInt(age.toString());
                Date birthDate = new Date();
                birthDate.setYear(birthDate.getYear() - years);
                patient.setBirthDate(birthDate);
            } catch (NumberFormatException ignored) {
                // Claude returned a non-numeric age — skip silently.
            }
        }

        Object gender = data.get("gender");
        if (gender instanceof String g && !g.isBlank()) {
            try {
                patient.setGender(Enumerations.AdministrativeGender.fromCode(g.toLowerCase()));
            } catch (Exception ignored) {
                // Unknown gender code — leave unset.
            }
        }

        return patient;
    }

    private Encounter buildEncounter(Map<String, Object> data, Reference patientRef) {
        Encounter encounter = new Encounter();
        encounter.setId(UUID.randomUUID().toString());
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);

        // class: AMB = ambulatory (outpatient visit). Required by FHIR.
        encounter.setClass_(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                .setCode("AMB")
                .setDisplay("ambulatory"));

        encounter.setSubject(patientRef);
        encounter.setPeriod(new Period().setStart(new Date()).setEnd(new Date()));

        if (data != null) {
            Object type = data.get("type");
            if (type instanceof String t && !t.isBlank()) {
                encounter.addType(new CodeableConcept().setText(t));
            }
            Object reason = data.get("reason");
            if (reason instanceof String r && !r.isBlank()) {
                encounter.addReasonCode(new CodeableConcept().setText(r));
            }
        }
        return encounter;
    }

    private Composition buildComposition(String summary, Reference patientRef, Reference encounterRef) {
        Composition composition = new Composition();
        composition.setId(UUID.randomUUID().toString());
        composition.setStatus(Composition.CompositionStatus.FINAL);
        composition.setDate(new Date());
        composition.setTitle("Visit Summary");
        composition.setSubject(patientRef);
        composition.setEncounter(encounterRef);
        composition.addAuthor(new Reference().setDisplay("RestfulRemedy AI"));

        // LOINC 51847-2 = "Evaluation + Plan note"
        composition.setType(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("51847-2")
                        .setDisplay("Evaluation + Plan note")));

        Composition.SectionComponent section = new Composition.SectionComponent();
        section.setTitle("Summary");
        Narrative narrative = new Narrative();
        narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
        narrative.setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\">"
                + escapeXml(summary) + "</div>");
        section.setText(narrative);
        composition.addSection(section);

        return composition;
    }

    private MedicationStatement buildMedicationStatement(Map<String, Object> med,
                                                         Reference patientRef,
                                                         Reference encounterRef) {
        MedicationStatement statement = new MedicationStatement();
        statement.setId(UUID.randomUUID().toString());
        statement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
        statement.setSubject(patientRef);
        statement.setContext(encounterRef);

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

        String dosage = (String) med.get("dosage");
        if (dosage != null && !dosage.isBlank()) {
            statement.addDosage(new Dosage().setText(dosage));
        }

        return statement;
    }

    private Condition buildCondition(Map<String, Object> dx,
                                     Reference patientRef,
                                     Reference encounterRef) {
        Condition condition = new Condition();
        condition.setId(UUID.randomUUID().toString());
        condition.setSubject(patientRef);
        condition.setEncounter(encounterRef);

        condition.setClinicalStatus(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical")
                        .setCode("active")
                        .setDisplay("Active")));

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

    private Observation buildObservation(Map<String, Object> obs,
                                         Reference patientRef,
                                         Reference encounterRef) {
        Observation observation = new Observation();
        observation.setId(UUID.randomUUID().toString());
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.setSubject(patientRef);
        observation.setEncounter(encounterRef);
        observation.setEffective(new DateTimeType(new Date()));

        String description = (String) obs.get("description");
        String loincCode = (String) obs.get("loinc_code");
        String value = (String) obs.get("value");

        CodeableConcept code = new CodeableConcept();
        code.setText(description);
        if (loincCode != null && !loincCode.isBlank()) {
            code.addCoding(new Coding()
                    .setSystem("http://loinc.org")
                    .setCode(loincCode)
                    .setDisplay(description));
        }
        observation.setCode(code);

        if (value != null && !value.isBlank()) {
            observation.setValue(new StringType(value));
        }

        return observation;
    }

    // -- helpers -------------------------------------------------------------

    /**
     * Adds a resource to the bundle with a urn:uuid fullUrl, returning the
     * fullUrl so other resources can reference it.
     */
    private String addEntry(Bundle bundle, Resource resource) {
        String fullUrl = "urn:uuid:" + resource.getIdElement().getIdPart();
        bundle.addEntry()
                .setFullUrl(fullUrl)
                .setResource(resource);
        return fullUrl;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object o) {
        return (o instanceof List) ? (List<Map<String, Object>>) o : List.of();
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
