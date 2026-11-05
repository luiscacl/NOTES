const express = require("express");
const fs = require("fs");
const path = require("path");
const bodyParser = require("body-parser");
const cors = require("cors");
const app = express();
const port = 3001;

const applicationsPath = path.join(__dirname, "applications");

app.use(cors());
app.use(bodyParser.json());

// Middleware to serve static files from the React app build
app.use(express.static(path.join(__dirname, "build")));

app.get("/api/applications", (req, res) => {
  fs.readdir(applicationsPath, (err, files) => {
    if (err) {
      return res
        .status(500)
        .json({ error: "Unable to read applications directory." });
    }

    const jobApplications = files.map((file) => {
      const filePath = path.join(applicationsPath, file);
      const data = fs.readFileSync(filePath, "utf-8");
      return JSON.parse(data);
    });

    res.status(200).json(jobApplications);
  });
});

app.post("/api/applications", (req, res) => {
  const application = req.body;

  const filename = `${application.company
    .toLowerCase()
    .split(" ")
    .join("_")}.json`;
  const filePath = path.join(applicationsPath, filename);

  fs.writeFile(filePath, JSON.stringify(application, null, 2), (err) => {
    if (err) {
      return res
        .status(500)
        .json({ error: "Unable to save the new job application." });
    }

    res
      .status(201)
      .json({ message: "Job application saved successfully.", id: filename });
  });
});

// Fallback route to serve the React app for any other request
app.get("*", (req, res) => {
  res.sendFile(path.join(__dirname, "build", "index.html"));
});

app.listen(port, () => {
  console.log(`Server is running on http://localhost:${port}`);
});
