from bs4 import BeautifulSoup
import requests
import time
from random import randint
import json

USER_AGENT = {'User-Agent':'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36'}

class SearchEngine:
    @staticmethod
    def search(query, sleep=True):
        if sleep:  
            time.sleep(randint(10, 100))
        temp_url = '+'.join(query.split())  
        url = 'https://www.duckduckgo.com/html/?q=' + temp_url + '&s=0'  
        results = []
        
        for page in range(2): 
            soup = BeautifulSoup(requests.get(url, headers=USER_AGENT).text, "html.parser")
            new_results = SearchEngine.scrape_search_result(soup)
            results.extend(new_results)
            
            if len(results) >= 10:
                break
            
            if sleep and page == 0: 
                time.sleep(randint(5, 15))
            
            url = f'https://www.duckduckgo.com/html/?q={temp_url}&s={30 * (page + 1)}'  # 下一页
        
        return results  

    @staticmethod
    def scrape_search_result(soup):
        raw_results = soup.find_all("div", class_="result")
        results = []
        for result in raw_results:
            if any(cls in result.get("class", []) for cls in ["result--ad", "result--carousel", "result--more"]):
                continue

            link_element = result.find("a", class_="result__a")
            if link_element:
                link = link_element.get('href')

                if any(ad_indicator in link for ad_indicator in ["ad_type", "ad_provider"]):
                    continue

                if link not in results:
                    results.append(link)
                    if len(results) == 10:
                        break
    
        return results

def read_queries(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        return [line.strip() for line in f if line.strip()]

def main():
    queries = read_queries('100QueriesSet4.txt')  
    
    results = {}
    for i, query in enumerate(queries, 1):
        query_results = SearchEngine.search(query)
        results[query] = query_results
        print(f"Finished scraping for query {i}/100: {query}. Found {len(query_results)} results.")
    
    with open('result.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

if __name__ == "__main__":
    main()