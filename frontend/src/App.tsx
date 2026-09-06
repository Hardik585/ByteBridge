import { useEffect, useState } from "react";
import axios from "axios";

import FileUpload from "./components/fileUpload.component";
import FileDownload from "./components/fileDownload.component";
import InviteCode from "./components/inviteCode.component";

function App() {
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [port, setPort] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<"upload" | "download">("upload");

  useEffect(() => {
    document.title = "PeerLink - P2P File Sharing";
  }, []);

  const handleFileUpload = async (file: File) => {
    setUploadedFile(file);
    setIsUploading(true);

    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await axios.post("/api/upload", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });

      setPort(response.data.port);
    } catch (error) {
      console.error("Error uploading file:", error);
      alert("Failed to upload file. Please try again.");
    } finally {
      setIsUploading(false);
    }
  };

  const handleDownload = async (port: number) => {
    setIsDownloading(true);

    try {
      const response = await axios.get(`/api/download/${port}`, {
        responseType: "blob",
      });

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");

      link.href = url;

      let filename = "downloaded-file";
      const contentDisposition = response.headers["content-disposition"];

      if (contentDisposition) {
        const match = contentDisposition.match(/filename="(.+)"/);

        if (match) {
          filename = match[1];
        }
      }

      link.download = filename;

      document.body.appendChild(link);
      link.click();
      link.remove();

      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error("Error downloading file:", error);
      alert("Failed to download file. Please check the invite code and try again.");
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <main className="min-h-screen bg-gray-50">
      <div className="container mx-auto max-w-4xl px-4 py-8">
        <header className="mb-12 text-center">
          <h1 className="mb-2 text-4xl font-bold text-blue-600">
            ByteShare
          </h1>
          <p className="text-xl text-gray-600">
            Secure P2P File Sharing
          </p>
        </header>

        <div className="rounded-lg bg-white p-6 shadow-lg">
          <div className="mb-6 flex border-b">
            <button
              className={`px-4 py-2 font-medium ${
                activeTab === "upload"
                  ? "border-b-2 border-blue-600 text-blue-600"
                  : "text-gray-500 hover:text-gray-700"
              }`}
              onClick={() => setActiveTab("upload")}
            >
              Share a File
            </button>

            <button
              className={`px-4 py-2 font-medium ${
                activeTab === "download"
                  ? "border-b-2 border-blue-600 text-blue-600"
                  : "text-gray-500 hover:text-gray-700"
              }`}
              onClick={() => setActiveTab("download")}
            >
              Receive a File
            </button>
          </div>

          {activeTab === "upload" ? (
            <>
              <FileUpload
                onFileUpload={handleFileUpload}
                isUploading={isUploading}
              />

              {uploadedFile && !isUploading && (
                <div className="mt-4 rounded-md bg-gray-50 p-3">
                  <p className="text-sm text-gray-600">
                    Selected file:
                    <span className="font-medium">
                      {" "}
                      {uploadedFile.name}
                    </span>{" "}
                    ({Math.round(uploadedFile.size / 1024)} KB)
                  </p>
                </div>
              )}

              {isUploading && (
                <div className="mt-6 text-center">
                  <div className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-blue-500 border-t-transparent"></div>
                  <p className="mt-2 text-gray-600">
                    Uploading file...
                  </p>
                </div>
              )}

              <InviteCode port={port} />
            </>
          ) : (
            <>
              <FileDownload
                onDownload={handleDownload}
                isDownloading={isDownloading}
              />

              {isDownloading && (
                <div className="mt-6 text-center">
                  <div className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-blue-500 border-t-transparent"></div>
                  <p className="mt-2 text-gray-600">
                    Downloading file...
                  </p>
                </div>
              )}
            </>
          )}
        </div>

        <footer className="mt-12 text-center text-sm text-gray-500">
          <p>
            ByteShare &copy; {new Date().getFullYear()} - Secure P2P File Sharing
          </p>
        </footer>
      </div>
    </main>
  );
}

export default App;