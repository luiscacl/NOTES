import { Checkbox } from "antd";
import data from "../data.json";

const Checklist = ({ toggle, isCompleted }) => {
  return (
    <div style={{ padding: "20px 40px" }}>
      <h2>Checklist antes de la entrevista</h2>
      {data.checklist.map((item, i) =>
        item ? (
          <div style={{ display: "block", marginBottom: "12px" }}>
            <Checkbox
              key={i}
              checked={isCompleted(item)}
              onClick={() => toggle(item)}
            >
              <span
                style={{
                  fontSize: i === data.checklist.length - 1 ? "18px" : "14px",
                }}
              >
                {item}
              </span>
            </Checkbox>
          </div>
        ) : (
          <br />
        )
      )}
    </div>
  );
};

export default Checklist;
