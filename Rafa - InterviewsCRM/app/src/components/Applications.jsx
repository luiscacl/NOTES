import React, { useState, useMemo } from "react";
import { Button, Collapse } from "antd";
import JobApplication from "./JobApplication";
import { FormOutlined } from "@ant-design/icons";

const defaultData = {
  company: "",
  website: "",
  jobLink: "",
  position: "Senior Full-Stack Engineer",
  techStack: "",
  status: "En espera",
  stage: "Apliqué",
  jobType: "Contractor",
  ISR: "2.5",
  teamSize: "",
  applicationDate: new Date(new Date().setHours(new Date().getHours() - 6))
    .toISOString()
    .split("T")[0],
  industry: "Nearshoring",
  benefits: "",
  notes: "",
  hourlySalaryUSD: "",
  monthlySalaryUSD: "",
  annualSalaryUSD: "",
  monthlySalaryMXN: "",
  annualSalaryMXN: "",
  monthlyNetSalaryMXN: "",
  annualNetSalaryMXN: "",
};

const Applications = ({ jobs, USD, handleFormSubmit, goToInterview }) => {
  const [isCreatingNewApplication, setIsCreatingNewApplication] =
    useState(false);

  const totalApplications = useMemo(
    () =>
      Object.values(jobs)
        .map((job) => job.length)
        .reduce((curr, prev) => prev + curr, 0),
    [jobs]
  );

  return (
    <div style={{ padding: "20px 40px" }}>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <h1>Job Applications ({totalApplications})</h1>
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            gap: 16,
          }}
        >
          <h3>USD Rate: ${USD.toFixed(2)}</h3>
          <Button
            type="primary"
            onClick={() =>
              setIsCreatingNewApplication(!isCreatingNewApplication)
            }
          >
            New Application
          </Button>
        </div>
      </div>

      {isCreatingNewApplication ? (
        <JobApplication
          jobData={defaultData}
          USD={USD}
          onSubmit={(newData) => {
            handleFormSubmit(newData);
            setIsCreatingNewApplication(false);
          }}
        />
      ) : null}

      {Object.values(jobs).map((jobsType) => (
        <Collapse accordion style={{ marginBottom: 32 }}>
          {jobsType.map((job) => (
            <Collapse.Panel
              header={
                <div style={{ display: "flex" }}>
                  <span style={{ width: "80%" }}>{job.company}</span>
                  <span style={{ width: "100%" }}>{job.position}</span>
                  <span style={{ width: "50%" }}>{job.status}</span>
                  <span style={{ width: "70%" }}>{job.stage}</span>
                  <span style={{ width: "50%" }}>
                    {job.annualSalaryUSD ? `${job.annualSalaryUSD} USD` : ""}
                  </span>
                  <span
                    style={{ width: "10%" }}
                    onClick={() => goToInterview(job)}
                  >
                    <FormOutlined />
                  </span>
                </div>
              }
              key={job.company}
            >
              <JobApplication
                jobData={job}
                USD={USD}
                onSubmit={handleFormSubmit}
              />
            </Collapse.Panel>
          ))}
        </Collapse>
      ))}
    </div>
  );
};

export default Applications;
