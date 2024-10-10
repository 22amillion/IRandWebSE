import json
import csv
import re

def load_json(file_path):
    with open(file_path, 'r') as f:
        return json.load(f)

def normalize_url(url):
    # remove http:// or https://
    url = re.sub(r'^https?://', '', url.lower())
    # remove "/"
    return url.rstrip('/')

def calculate_overlap(list1, list2):
    set1 = set(normalize_url(url) for url in list1)
    set2 = set(normalize_url(url) for url in list2)
    overlap = set1.intersection(set2)
    return len(overlap), (len(overlap) / len(list1)) * 100

def calculate_spearman(list1, list2):
    norm_list1 = [normalize_url(url) for url in list1]
    norm_list2 = [normalize_url(url) for url in list2]
    common_elements = set(norm_list1).intersection(norm_list2)
    n = len(common_elements)
    
    if n <= 1:
        return 1 if n == 1 and norm_list1.index(next(iter(common_elements))) == norm_list2.index(next(iter(common_elements))) else 0
    
    rank1 = [norm_list1.index(x) for x in common_elements]
    rank2 = [norm_list2.index(x) for x in common_elements]
    
    d_squared_sum = sum((r1 - r2) ** 2 for r1, r2 in zip(rank1, rank2))
    
    rho = 1 - (6 * d_squared_sum) / (n * (n**2 - 1))
    return rho

def main():
    your_results = load_json('result.json')
    google_results = load_json('Google_Result4.json')
    
    output = []
    total_overlap = 0
    total_percent = 0
    total_spearman = 0
    
    for i, (query, your_urls) in enumerate(your_results.items(), 1):
        google_urls = google_results[query]
        overlap, percent = calculate_overlap(your_urls, google_urls)
        spearman = calculate_spearman(your_urls, google_urls)
        
        output.append([f"Query {i}", overlap, round(percent, 1), round(spearman, 2)])
        
        total_overlap += overlap
        total_percent += percent
        total_spearman += spearman
    
    num_queries = len(your_results)
    avg_overlap = total_overlap / num_queries
    avg_percent = total_percent / num_queries
    avg_spearman = total_spearman / num_queries
    
    output.append(["Averages", round(avg_overlap, 1), round(avg_percent, 1), round(avg_spearman, 2)])
    
    with open('result.csv', 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(["Queries", "Number of Overlapping Results", "Percent Overlap", "Spearman Coefficient"])
        writer.writerows(output)

if __name__ == "__main__":
    main()