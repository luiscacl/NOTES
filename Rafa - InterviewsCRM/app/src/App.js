import { useEffect, useState } from "react";
import Applications from "./components/Applications";
import { Menu, message } from "antd";
import {
  FormOutlined,
  HomeOutlined,
  PartitionOutlined,
  QuestionCircleOutlined,
  StarOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import axios from "axios";
import Interview from "./components/Interview";
import Checklist from "./components/Checklist";
import Questions from "./components/Questions";
import StarMethod from "./components/StarMethod";
import SystemsDesign from "./components/SystemsDesign";

const PAGES = {
  APPLICATIONS: "APPLICATIONS",
  CHECKLIST: "CHECKLIST",
  INTERVIEW: "INTERVIEW",
  QUESTIONS: "QUESTIONS",
  STAR_METHOD: "STAR_METHOD",
  SYSTEMS_DESIGN: "SYSTEMS_DESIGN",
};

const COMPONENTS = {
  [PAGES.APPLICATIONS]: Applications,
  [PAGES.CHECKLIST]: Checklist,
  [PAGES.INTERVIEW]: Interview,
  [PAGES.QUESTIONS]: Questions,
  [PAGES.STAR_METHOD]: StarMethod,
  [PAGES.SYSTEMS_DESIGN]: SystemsDesign,
};

const getKey = (string) => string.split(" ").join("_");

const items = [
  {
    label: "All Applications",
    key: PAGES.APPLICATIONS,
    icon: <HomeOutlined />,
  },
  {
    label: "Checklist",
    key: PAGES.CHECKLIST,
    icon: <UnorderedListOutlined />,
  },
  {
    label: "Interview",
    key: PAGES.INTERVIEW,
    icon: <FormOutlined />,
  },
  {
    label: "Common asked questions",
    key: PAGES.QUESTIONS,
    icon: <QuestionCircleOutlined />,
  },
  {
    label: "Conflicts resolution with STAR",
    key: PAGES.STAR_METHOD,
    icon: <StarOutlined />,
  },
  {
    label: "Systems Design",
    key: PAGES.SYSTEMS_DESIGN,
    icon: <PartitionOutlined />,
  },
];

function App() {
  const [currentPage, setCurrentPage] = useState(PAGES.APPLICATIONS);
  const [jobs, setJobs] = useState({});
  const [selectedJob, setSelectedJob] = useState(null);
  const [USD, setUSD] = useState(0);
  const [itemsCompleted, setItemsCompleted] = useState({});

  const [trigger, setTrigger] = useState(false);

  useEffect(() => {
    axios
      .get("http://localhost:3001/api/applications")
      .then((response) => {
        const jobsData = response.data;
        const enEspera = [];
        const activo = [];
        const rechazado = [];

        jobsData.sort((a, b) => {
          const dateA = new Date(a.applicationDate);
          const dateB = new Date(b.applicationDate);
          return dateB - dateA;
        });

        for (const job of jobsData) {
          if (job.status === "En espera") enEspera.push(job);
          if (job.status === "Activo") activo.push(job);
          if (job.status === "Rechazado") rechazado.push(job);
        }

        setJobs({
          activo,
          enEspera,
          rechazado,
        });
      })
      .catch((error) => {
        message.error("Failed to load job applications.");
      });
  }, [trigger]);

  useEffect(() => {
    axios
      .get("https://open.er-api.com/v6/latest/USD")
      .then((response) => {
        setUSD(response.data.rates.MXN);
      })
      .catch((error) => {
        message.error("Failed to load USD rate.");
      });
  }, []);

  const handleFormSubmit = async (body) => {
    await axios.post("http://localhost:3001/api/applications", body);
    setTrigger(!trigger);
    message.success("Form submitted successfully.");
  };

  const goToInterview = (job) => {
    setCurrentPage(PAGES.INTERVIEW);
    setSelectedJob(job);
    setItemsCompleted({});
  };

  const toggle = (item) => {
    const key = getKey(item);
    const modifiedItemsCompleted = { ...itemsCompleted };
    if (modifiedItemsCompleted[key]) modifiedItemsCompleted[key] = false;
    else modifiedItemsCompleted[key] = true;
    setItemsCompleted(modifiedItemsCompleted);
  };

  const isCompleted = (item) => {
    const key = getKey(item);
    return itemsCompleted[key] === true;
  };

  const Component = COMPONENTS[currentPage];

  return (
    <div>
      <Menu
        onClick={(e) => setCurrentPage(e.key)}
        selectedKeys={[currentPage]}
        mode="horizontal"
        items={items}
      />
      <Component
        jobs={jobs}
        job={selectedJob}
        USD={USD}
        handleFormSubmit={handleFormSubmit}
        goToInterview={goToInterview}
        itemsCompleted={itemsCompleted}
        setItemsCompleted={setItemsCompleted}
        toggle={toggle}
        isCompleted={isCompleted}
      />
    </div>
  );
}

export default App;
