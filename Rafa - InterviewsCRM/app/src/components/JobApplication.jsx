import React, { useEffect, useRef, useState } from "react";
import { Form, Input, Select, Button } from "antd";
import { LinkOutlined } from "@ant-design/icons";

const { TextArea } = Input;
const { Option } = Select;

const JobApplication = ({ jobData, USD, onSubmit, textAreSizes = 4 }) => {
  const [form] = Form.useForm();
  const industryRef = useRef();
  const benefitsRef = useRef();
  const notesRef = useRef();

  useEffect(() => {
    if (jobData) {
      form.setFieldsValue({ ...jobData });
    }
  }, [jobData, form]);

  const [salaries, setSalaries] = useState({
    hourlySalaryUSD: jobData.hourlySalaryUSD || "",
    monthlySalaryUSD: jobData.monthlySalaryUSD || "",
    annualSalaryUSD: jobData.annualSalaryUSD || "",
    monthlySalaryMXN: jobData.monthlySalaryMXN || "",
    annualSalaryMXN: jobData.annualSalaryMXN || "",
  });

  const onSubmitHandler = async (formData) => {
    const ISR = Number(formData.ISR);
    const monthlyNetSalaryMXN =
      parseCurrency(salaries.monthlySalaryMXN) * (100 - ISR) * 0.01;
    const annualNetSalaryMXN =
      parseCurrency(salaries.annualSalaryMXN) * (100 - ISR) * 0.01;

    const newData = {
      ...formData,
      ...salaries,
      monthlyNetSalaryMXN: formatCurrency(monthlyNetSalaryMXN),
      annualNetSalaryMXN: formatCurrency(annualNetSalaryMXN),
    };
    onSubmit(newData);
  };

  const formatCurrency = (value) => {
    if (!value) return "0";
    const number = Number(Math.round(value)).toLocaleString();
    if (number === 0) return "0";
    return `$${number}`;
  };

  const parseCurrency = (value) => {
    if (!value) return 0;
    return Number(value.toString().replace(/[^0-9.-]+/g, ""));
  };

  const calculateSalaries = (updatedSalaries) => {
    const hourlyUSD = parseCurrency(updatedSalaries.hourlySalaryUSD);
    const annualUSD = hourlyUSD * 40 * 52;
    const monthlyUSD = annualUSD / 12;

    const annualMXN = annualUSD * USD;
    const monthlyMXN = annualMXN / 12;

    return {
      hourlySalaryUSD: formatCurrency(hourlyUSD),
      monthlySalaryUSD: formatCurrency(monthlyUSD),
      annualSalaryUSD: formatCurrency(annualUSD),
      monthlySalaryMXN: formatCurrency(monthlyMXN),
      annualSalaryMXN: formatCurrency(annualMXN),
    };
  };

  const handleSalaryChange = (name, value) => {
    const parsedValue = parseCurrency(value);
    let updatedSalaries;

    if (name === "hourlySalaryUSD") {
      updatedSalaries = calculateSalaries({ hourlySalaryUSD: parsedValue });
    } else if (name === "monthlySalaryUSD") {
      const annualUSD = parsedValue * 12;
      const hourlyUSD = annualUSD / (40 * 52);
      updatedSalaries = calculateSalaries({ hourlySalaryUSD: hourlyUSD });
    } else if (name === "annualSalaryUSD") {
      const hourlyUSD = parsedValue / (40 * 52);
      updatedSalaries = calculateSalaries({ hourlySalaryUSD: hourlyUSD });
    } else {
      const annualMXN =
        name === "monthlySalaryMXN" ? parsedValue * 12 : parsedValue;
      const hourlyMXN = annualMXN / (40 * 52);
      const correspondingUSD = hourlyMXN / USD;
      updatedSalaries = calculateSalaries({
        hourlySalaryUSD: correspondingUSD,
      });
    }

    setSalaries(updatedSalaries);
  };

  useEffect(() => {
    if (!industryRef.current?.resizableTextArea?.textArea) return;
    setTimeout(() => {
      const textArea = industryRef.current.resizableTextArea.textArea;
      const height = textArea.scrollHeight + 4;
      textArea.style.height = `${height}px`;
    }, 0);
  }, []);

  useEffect(() => {
    if (!benefitsRef.current?.resizableTextArea?.textArea) return;
    setTimeout(() => {
      const textArea = benefitsRef.current.resizableTextArea.textArea;
      const height = textArea.scrollHeight + 4;
      textArea.style.height = `${height}px`;
    }, 0);
  }, []);

  useEffect(() => {
    if (!notesRef.current?.resizableTextArea?.textArea) return;
    setTimeout(() => {
      const textArea = notesRef.current.resizableTextArea.textArea;
      const height = textArea.scrollHeight + 4;
      textArea.style.height = `${height}px`;
    }, 0);
  }, []);

  return (
    <Form
      form={form}
      layout="vertical"
      style={{ width: "100%" }}
      onFinish={onSubmitHandler}
    >
      <div style={{ width: "100%", display: "flex", gap: 24 }}>
        <Form.Item label="Empresa" name="company" style={{ width: "30%" }}>
          <Input />
        </Form.Item>

        <Form.Item
          label={
            <div style={{ display: "flex", gap: 12 }}>
              <span>Website</span>
              <a href={jobData.website} target="_blank" rel="noreferrer">
                <LinkOutlined />
              </a>
            </div>
          }
          name="website"
          style={{ width: "30%" }}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label={
            <div style={{ display: "flex", gap: 12 }}>
              <span>Link de la Vacante</span>
              <a href={jobData.jobLink} target="_blank" rel="noreferrer">
                <LinkOutlined />
              </a>
            </div>
          }
          name="jobLink"
          style={{ width: "30%" }}
        >
          <Input />
        </Form.Item>

        <Form.Item label="Posición" name="position" style={{ width: "50%" }}>
          <Input />
        </Form.Item>

        <Form.Item
          label="Tech stack"
          name="techStack"
          style={{ width: "100%" }}
        >
          <Input />
        </Form.Item>
      </div>

      <div style={{ width: "100%", display: "flex", gap: 24 }}>
        <Form.Item label="Status" name="status" style={{ width: "100%" }}>
          <Select>
            <Option value="En espera">En espera</Option>
            <Option value="Activo">Activo</Option>
            <Option value="Rechazado">Rechazado</Option>
          </Select>
        </Form.Item>

        <Form.Item label="Etapa" name="stage" style={{ width: "100%" }}>
          <Select>
            <Option value="Apliqué">Apliqué</Option>
            <Option value="Entrevista con Reclutador">
              Entrevista con Reclutador
            </Option>
            <Option value="Code Challenge">Code Challenge</Option>
            <Option value="Entrevista con Manager">
              Entrevista con Manager
            </Option>
            <Option value="Recibí Oferta">Recibí Oferta</Option>
            <Option value="Contratado">Contratado</Option>
          </Select>
        </Form.Item>

        <Form.Item
          label="Tipo de empleo"
          name="jobType"
          style={{ width: "100%" }}
        >
          <Select>
            <Option value="Contractor">Contractor</Option>
            <Option value="Full-time">Full-time</Option>
            <Option value="Part-time">Part-time</Option>
          </Select>
        </Form.Item>

        <Form.Item label="Tasa de ISR" name="ISR" style={{ width: "60%" }}>
          <Select>
            <Option value="2.5">2.5%</Option>
            <Option value="30">30%</Option>
          </Select>
        </Form.Item>

        <Form.Item
          label="Tamaño del equipo"
          name="teamSize"
          style={{ width: "60%" }}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="Fecha de aplicación"
          name="applicationDate"
          style={{ width: "100%" }}
        >
          <Input />
        </Form.Item>
      </div>

      <div style={{ width: "100%", display: "flex", gap: 24 }}>
        <Form.Item
          label="Giro de la empresa"
          name="industry"
          style={{ width: "100%" }}
        >
          <TextArea ref={industryRef} rows={textAreSizes} />
        </Form.Item>

        <Form.Item label="Beneficios" name="benefits" style={{ width: "100%" }}>
          <TextArea ref={benefitsRef} rows={textAreSizes} />
        </Form.Item>

        <Form.Item label="Notas" name="notes" style={{ width: "100%" }}>
          <TextArea ref={notesRef} rows={textAreSizes} />
        </Form.Item>
      </div>

      <div style={{ width: "100%", display: "flex", gap: 24 }}>
        <Form.Item
          label="Por hora en USD"
          name="hourlySalaryUSD"
          style={{ width: "60%" }}
        >
          <Input
            value={salaries.hourlySalaryUSD}
            onChange={(e) =>
              handleSalaryChange("hourlySalaryUSD", e.target.value)
            }
          />
        </Form.Item>

        <Form.Item
          label="Mensual en USD"
          name="monthlySalaryUSD"
          style={{ width: "60%" }}
        >
          <Input
            value={salaries.monthlySalaryUSD}
            onChange={(e) =>
              handleSalaryChange("monthlySalaryUSD", e.target.value)
            }
          />
        </Form.Item>

        <Form.Item
          label="Anual en USD"
          name="annualSalaryUSD"
          style={{ width: "60%" }}
        >
          <Input
            value={salaries.annualSalaryUSD}
            onChange={(e) =>
              handleSalaryChange("annualSalaryUSD", e.target.value)
            }
          />
        </Form.Item>

        <Form.Item
          label="Mensual en MXN"
          name="monthlySalaryMXN"
          style={{ width: "60%" }}
        >
          <Input
            value={salaries.monthlySalaryMXN}
            onChange={(e) =>
              handleSalaryChange("monthlySalaryMXN", e.target.value)
            }
          />
        </Form.Item>

        <Form.Item
          label="Anual en MXN"
          name="annualSalaryMXN"
          style={{ width: "60%" }}
        >
          <Input
            value={salaries.annualSalaryMXN}
            onChange={(e) =>
              handleSalaryChange("annualSalaryMXN", e.target.value)
            }
          />
        </Form.Item>

        <Form.Item
          label="Mensual Neto en MXN"
          name="monthlyNetSalaryMXN"
          style={{ width: "100%" }}
        >
          <Input style={{ width: "100%" }} />
        </Form.Item>

        <Form.Item
          label="Anual Neto en MXN"
          name="annualNetSalaryMXN"
          style={{ width: "100%" }}
        >
          <Input style={{ width: "100%" }} />
        </Form.Item>
      </div>

      <Form.Item>
        <Button type="primary" htmlType="submit">
          Save
        </Button>
      </Form.Item>
    </Form>
  );
};

export default JobApplication;
