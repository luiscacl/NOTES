import { Collapse } from "antd";
import data from "../data.json";

const Questions = () => {
  return (
    <div style={{ padding: "20px 40px" }}>
      <h2>Common asked questions</h2>
      <Collapse accordion style={{ marginBottom: 32 }}>
        {data.questions.map(({ question, answer }, i) => (
          <Collapse.Panel header={question} key={i}>
            {answer}
          </Collapse.Panel>
        ))}
      </Collapse>
    </div>
  );
};

export default Questions;
