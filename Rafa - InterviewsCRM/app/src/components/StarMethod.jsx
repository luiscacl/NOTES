import { Collapse } from "antd";
import data from "../data.json";

const StarMethod = () => {
  return (
    <div style={{ padding: "20px 40px" }}>
      <h2>Resolución de conflictos con el método STAR</h2>
      <Collapse accordion style={{ marginBottom: 32 }}>
        {data.conflicts.map((conflict) => (
          <Collapse.Panel header={conflict.title} key={conflict.title}>
            <ul>
              <li>
                <strong>Situación: </strong>
                {conflict.situation}
              </li>
              <br />
              <li>
                <strong>Tarea: </strong>
                {conflict.task}
              </li>
              <br />
              <li>
                <strong>Acción: </strong>
                {conflict.action}
              </li>
              <br />
              <li>
                <strong>Resultado: </strong>
                {conflict.result}
              </li>
            </ul>
          </Collapse.Panel>
        ))}
      </Collapse>
    </div>
  );
};

export default StarMethod;
