import { FilePlus2, FolderOpen, Trash2, Upload } from "lucide-react";
import { useRef, useState } from "react";

import type { UploadFile } from "../../api/contracts/source-imports";

const SUPPORTED_EXTENSIONS = new Set([
  "doc",
  "docx",
  "pptx",
  "pdf",
  "xlsx",
  "xls",
  "csv",
  "md",
  "markdown",
  "txt",
  "json",
  "html",
  "xml",
  "yml",
  "yaml",
  "properties",
  "js",
  "css",
  "java",
  "sh",
  "py",
  "sql",
]);

interface SourceFilePickerProps {
  files: UploadFile[];
  onChange: (files: UploadFile[]) => void;
}

export function SourceFilePicker({ files, onChange }: SourceFilePickerProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const directoryInputRef = useRef<HTMLInputElement>(null);
  const [rejectedCount, setRejectedCount] = useState(0);
  const [dragActive, setDragActive] = useState(false);

  const addFiles = (incoming: UploadFile[]) => {
    const accepted = incoming.filter(({ file }) => isSupported(file.name));
    setRejectedCount(incoming.length - accepted.length);
    onChange(mergeFiles(files, accepted));
  };

  const handleDrop = async (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragActive(false);
    addFiles(await readDroppedFiles(event.dataTransfer));
  };

  return (
    <div
      className={`source-file-picker${dragActive ? " is-dragging" : ""}`}
      onDragEnter={(event) => {
        event.preventDefault();
        setDragActive(true);
      }}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
          setDragActive(false);
        }
      }}
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => void handleDrop(event)}
    >
      <input
        accept={acceptedExtensions()}
        aria-label="选择文件"
        className="sr-only"
        multiple
        onChange={(event) => {
          addFiles(toUploadFiles(event.target.files));
          event.target.value = "";
        }}
        ref={fileInputRef}
        type="file"
      />
      <input
        {...{ webkitdirectory: "" }}
        aria-label="选择文件夹"
        className="sr-only"
        multiple
        onChange={(event) => {
          addFiles(toUploadFiles(event.target.files));
          event.target.value = "";
        }}
        ref={directoryInputRef}
        type="file"
      />
      <Upload aria-hidden="true" size={22} strokeWidth={1.75} />
      <div className="source-file-picker-actions">
        <button
          className="secondary-button compact-button"
          onClick={() => fileInputRef.current?.click()}
          type="button"
        >
          <FilePlus2 aria-hidden="true" size={17} />
          选择文件
        </button>
        <button
          className="secondary-button compact-button"
          onClick={() => directoryInputRef.current?.click()}
          type="button"
        >
          <FolderOpen aria-hidden="true" size={17} />
          选择文件夹
        </button>
      </div>
      <span aria-live="polite" className="source-file-summary">
        {files.length > 0
          ? `${files.length} 个文件，${formatBytes(totalBytes(files))}`
          : "尚未选择文件"}
      </span>
      {rejectedCount > 0 ? (
        <span className="source-file-rejected">已忽略 {rejectedCount} 个不支持的文件</span>
      ) : null}
      {files.length > 0 ? (
        <ul className="source-file-list">
          {files.map((item, index) => (
            <li key={`${item.path}-${item.file.size}-${item.file.lastModified}`}>
              <span title={item.path}>{item.path}</span>
              <button
                aria-label={`移除 ${item.path}`}
                className="icon-button"
                onClick={() => onChange(files.filter((_, itemIndex) => itemIndex !== index))}
                title="移除文件"
                type="button"
              >
                <Trash2 aria-hidden="true" size={16} />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

function acceptedExtensions() {
  return [...SUPPORTED_EXTENSIONS].map((extension) => `.${extension}`).join(",");
}

function toUploadFiles(fileList: FileList | null): UploadFile[] {
  return Array.from(fileList ?? []).map((file) => ({
    file,
    path: normalizePath(file.webkitRelativePath || file.name),
  }));
}

function mergeFiles(current: UploadFile[], incoming: UploadFile[]) {
  const result = [...current];
  const keys = new Set(current.map(fileKey));
  incoming.forEach((item) => {
    const key = fileKey(item);
    if (!keys.has(key)) {
      result.push(item);
      keys.add(key);
    }
  });
  return result;
}

function fileKey(item: UploadFile) {
  return `${item.path}:${item.file.size}:${item.file.lastModified}`;
}

function isSupported(name: string) {
  const extension = name.toLowerCase().split(".").pop();
  return extension ? SUPPORTED_EXTENSIONS.has(extension) : false;
}

function totalBytes(files: UploadFile[]) {
  return files.reduce((total, item) => total + item.file.size, 0);
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

async function readDroppedFiles(dataTransfer: DataTransfer) {
  const entries = Array.from(dataTransfer.items)
    .map((item) => getEntry(item))
    .filter((entry): entry is LegacyFileSystemEntry => entry !== null);
  if (entries.length === 0) {
    return toUploadFiles(dataTransfer.files);
  }
  const nested = await Promise.all(entries.map((entry) => readEntry(entry, "")));
  return nested.flat();
}

function getEntry(item: DataTransferItem) {
  const candidate = item as unknown as {
    webkitGetAsEntry?: () => LegacyFileSystemEntry | null;
  };
  return candidate.webkitGetAsEntry?.() ?? null;
}

interface LegacyFileSystemEntry {
  isFile: boolean;
  isDirectory: boolean;
  name: string;
}

interface LegacyFileSystemFileEntry extends LegacyFileSystemEntry {
  file: (success: (file: File) => void, failure: () => void) => void;
}

interface LegacyFileSystemDirectoryEntry extends LegacyFileSystemEntry {
  createReader: () => {
    readEntries: (
      success: (entries: LegacyFileSystemEntry[]) => void,
      failure: () => void,
    ) => void;
  };
}

async function readEntry(entry: LegacyFileSystemEntry, parentPath: string): Promise<UploadFile[]> {
  const path = normalizePath(parentPath ? `${parentPath}/${entry.name}` : entry.name);
  if (entry.isFile) {
    const file = await readFile(entry as LegacyFileSystemFileEntry);
    return file ? [{ file, path }] : [];
  }
  if (!entry.isDirectory) return [];
  const children = await readAllEntries(entry as LegacyFileSystemDirectoryEntry);
  const nested = await Promise.all(children.map((child) => readEntry(child, path)));
  return nested.flat();
}

function readFile(entry: LegacyFileSystemFileEntry) {
  return new Promise<File | null>((resolve) => entry.file(resolve, () => resolve(null)));
}

function readAllEntries(entry: LegacyFileSystemDirectoryEntry) {
  const reader = entry.createReader();
  return new Promise<LegacyFileSystemEntry[]>((resolve) => {
    const result: LegacyFileSystemEntry[] = [];
    const readBatch = () => {
      reader.readEntries((entries) => {
        if (entries.length === 0) {
          resolve(result);
          return;
        }
        result.push(...entries);
        readBatch();
      }, () => resolve(result));
    };
    readBatch();
  });
}

function normalizePath(path: string) {
  return path.replaceAll("\\", "/").replace(/^\/+/, "").replace(/\/+$/, "");
}
