import JobApplication from "./JobApplication";
import data from "../data.json";
import { Checkbox } from "antd";

const Interview = ({ job, USD, handleFormSubmit, toggle, isCompleted }) => {
  if (!job) return null;

  return (
    <div style={{ padding: "20px 40px 40px" }}>
      <h1>{job.company}</h1>
      <JobApplication
        jobData={job}
        USD={USD}
        onSubmit={handleFormSubmit}
        textAreSizes={10}
      />
      <h4 style={{ marginTop: 40 }}>No te olvides de preguntar</h4>
      <div
        style={{
          display: "flex",
          flexWrap: "wrap",
          gap: "24px",
          margin: "24px 0",
        }}
      >
        {data.ask.map((item, i) => (
          <Checkbox
            key={i}
            checked={isCompleted(item)}
            onClick={() => toggle(item)}
          >
            {item}
          </Checkbox>
        ))}
      </div>
      <h4 style={{ marginTop: 32 }}>Negociación</h4>
      <p>
        Nunca aceptes una oferta por teléfono. Di que son muy buenas noticias,
        pero que te manden los detalles de la oferta por email para analizarlos
        con calma (lo que te permitirá analizar cómo pedir más).
      </p>
      <p>
        Me parece muy bien lo que me has propuesto y me siento muy contento por
        comenzar a trabajar, pero yo me sentiría más cómodo con esto números.
      </p>
      <h4 style={{ marginTop: 32 }}>Cosas a preguntar:</h4>
      <p>All benefits, insurance?</p>
      <p>
        Vacations & PTO, paid vacations? How many? After how many months? Local
        Hollidays?
      </p>
      <p>
        About taxes, will the company just make a bank transfer and I’ll take
        care of my own taxes?
      </p>
      <p>
        Will you send a laptop? If I’m goin to buy one, do I need any specific
        hardware capabilities?
      </p>
      <p>What are the next steps, what documents should I start collecting?</p>
      <p>When are you targeting to be my first day?</p>
      <p>Growth opportunities, bonuses</p>
    </div>
  );
};

export default Interview;
