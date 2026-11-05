import { Collapse } from "antd";
import data from "../data.json";
import { LinkOutlined } from "@ant-design/icons";

const SystemsDesign = () => {
  return (
    <div style={{ padding: "20px 40px" }}>
      <div
        style={{
          display: "flex",
          gap: "32px",
          alignItems: "center",
          margin: "6px 0 16px",
        }}
      >
        <h2 style={{ margin: 0 }}>Systems Design</h2>
        <div>
          <span>HelloInterview</span>
          <a
            href="https://www.hellointerview.com/learn/system-design/in-a-hurry/introduction"
            target="_blank"
            rel="noreferrer"
            style={{ marginLeft: 10, fontSize: 16 }}
          >
            <LinkOutlined />
          </a>
        </div>
        <div>
          <span>Excalidraw</span>
          <a
            href="https://excalidraw.com/"
            target="_blank"
            rel="noreferrer"
            style={{ marginLeft: 10, fontSize: 16 }}
          >
            <LinkOutlined />
          </a>
        </div>
      </div>

      <Collapse accordion>
        {data.systemsDesign.map(({ section, content }) => (
          <Collapse.Panel header={section} key={section}>
            {content.map((item, i) =>
              item ? (
                <div key={i} dangerouslySetInnerHTML={{ __html: item }} />
              ) : (
                <br />
              )
            )}
          </Collapse.Panel>
        ))}

        <Collapse.Panel header="Examples">
          <Collapse accordion>
            {data.systemsDesignExamples.map((example) => (
              <Collapse.Panel header={example.name} key={example.name}>
                <div style={{ display: "flex", justifyContent: "center" }}>
                  <img
                    alt={example.name}
                    src={`${example.name}.png`}
                    width={example.customWidth || "100%"}
                  />
                </div>
              </Collapse.Panel>
            ))}
          </Collapse>
        </Collapse.Panel>
      </Collapse>
    </div>
  );
};

export default SystemsDesign;
